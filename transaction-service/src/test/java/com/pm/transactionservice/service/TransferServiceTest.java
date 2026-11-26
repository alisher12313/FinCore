package com.pm.transactionservice.service;

import com.pm.transactionservice.client.AccountTransferClient;
import com.pm.transactionservice.client.dto.AccountResponseDto;
import com.pm.transactionservice.client.dto.InternalTransferRequestDto;
import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.entity.sql.CurrencyType;
import com.pm.transactionservice.entity.sql.Transaction;
import com.pm.transactionservice.entity.sql.TransactionStatus;
import com.pm.transactionservice.events.KafkaEventPublisher;
import com.pm.transactionservice.exception.CurrencyMismatchException;
import com.pm.transactionservice.exception.DuplicateIdempotencyKeyException;
import com.pm.transactionservice.exception.TransferFailedException;
import com.pm.transactionservice.mapper.TransferMapper;
import com.pm.transactionservice.repository.nosql.AuditLogRepository;
import com.pm.transactionservice.repository.sql.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String KEY = "client-key-1";
    private static final String SCOPED_KEY = USER_ID + ":" + KEY;
    private static final String REDIS_KEY = "idempotency:" + SCOPED_KEY;

    @Mock private TransferRepository transferRepository;
    @Mock private AccountTransferClient accountTransferClient;
    @Mock private TransferMapper transferMapper;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private KafkaEventPublisher kafkaEventPublisher;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private TransferService transferService;

    private Jwt jwt;
    private CreateTransferRequestDto request;
    private AccountResponseDto sender;
    private AccountResponseDto receiver;
    private final List<TransactionStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("t").header("alg", "none")
                .subject(USER_ID.toString()).claim("email", "user@example.com")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();

        request = new CreateTransferRequestDto("KZ2", new BigDecimal("50"), CurrencyType.KZT, KEY);

        sender = account("KZ1", CurrencyType.KZT);
        receiver = account("KZ2", CurrencyType.KZT);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(accountTransferClient.getAccountByUserId(USER_ID)).thenReturn(ResponseEntity.ok(sender));
        when(accountTransferClient.getAccountByAccountNumber("KZ2")).thenReturn(ResponseEntity.ok(receiver));
        when(transferRepository.findByIdempotencyKey(SCOPED_KEY)).thenReturn(Optional.empty());

        // simulate the DB assigning an id; record the status at each save
        Answer<Transaction> saveAnswer = inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            savedStatuses.add(t.getStatus());
            return t;
        };
        when(transferRepository.saveAndFlush(any(Transaction.class))).thenAnswer(saveAnswer);
        when(transferRepository.save(any(Transaction.class))).thenAnswer(saveAnswer);
    }

    private static AccountResponseDto account(String number, CurrencyType currency) {
        AccountResponseDto dto = new AccountResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setAccountNumber(number);
        dto.setCurrency(currency);
        return dto;
    }

    @Test
    void createTransfer_WhenNewKey_ThenClaimsKeyMovesMoneyAndStoresDone() {
        when(valueOps.setIfAbsent(eq(REDIS_KEY), eq(TransferService.IN_PROGRESS), any(Duration.class))).thenReturn(true);

        Transaction result = transferService.createTransfer(request, jwt);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.DONE);
        assertThat(result.getIdempotencyKey()).isEqualTo(SCOPED_KEY);
        assertThat(savedStatuses).containsExactly(TransactionStatus.PENDING, TransactionStatus.DONE);
        verify(accountTransferClient, times(1)).internalTransfer(any(InternalTransferRequestDto.class));
        verify(valueOps).set(eq(REDIS_KEY), eq(result.getId().toString()), any(Duration.class));
    }

    @Test
    void createTransfer_WhenKeyAlreadyProcessed_ThenReturnsExistingWithoutMovingMoney() {
        Transaction existing = Transaction.builder().id(UUID.randomUUID()).status(TransactionStatus.DONE).build();
        when(valueOps.get(REDIS_KEY)).thenReturn(existing.getId().toString());
        when(transferRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        Transaction result = transferService.createTransfer(request, jwt);

        assertThat(result).isSameAs(existing);
        verify(accountTransferClient, never()).internalTransfer(any());
        verify(valueOps, never()).setIfAbsent(anyString(), any(), any(Duration.class));
    }

    @Test
    void createTransfer_WhenSameKeyInProgress_ThenConflictAndNoMoneyMoved() {
        when(valueOps.get(REDIS_KEY)).thenReturn(TransferService.IN_PROGRESS);
        when(valueOps.setIfAbsent(eq(REDIS_KEY), any(), any(Duration.class))).thenReturn(false);

        assertThrows(DuplicateIdempotencyKeyException.class, () -> transferService.createTransfer(request, jwt));
        verify(accountTransferClient, never()).internalTransfer(any());
    }

    @Test
    void createTransfer_WhenRedisLostKeyButDbHasIt_ThenReturnsDbRowWithoutMovingMoney() {
        when(valueOps.setIfAbsent(eq(REDIS_KEY), any(), any(Duration.class))).thenReturn(true);
        Transaction existing = Transaction.builder().id(UUID.randomUUID()).status(TransactionStatus.PENDING).build();
        when(transferRepository.saveAndFlush(any(Transaction.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(transferRepository.findByIdempotencyKey(SCOPED_KEY)).thenReturn(Optional.empty(), Optional.of(existing));

        Transaction result = transferService.createTransfer(request, jwt);

        assertThat(result).isSameAs(existing);
        verify(accountTransferClient, never()).internalTransfer(any());
    }

    @Test
    void createTransfer_WhenAccountServiceFails_ThenFailedStatusIsPersisted() {
        when(valueOps.setIfAbsent(eq(REDIS_KEY), any(), any(Duration.class))).thenReturn(true);
        doThrow(new RuntimeException("insufficient balance")).when(accountTransferClient).internalTransfer(any());

        assertThrows(TransferFailedException.class, () -> transferService.createTransfer(request, jwt));

        assertThat(savedStatuses).containsExactly(TransactionStatus.PENDING, TransactionStatus.FAILED);
        // the key now points to the FAILED transfer, so a retry with the same key won't move money
        verify(valueOps).set(eq(REDIS_KEY), anyString(), any(Duration.class));
    }

    @Test
    void createTransfer_WhenCurrencyDiffersFromSenderAccount_ThenRejectAndReleaseKey() {
        when(valueOps.setIfAbsent(eq(REDIS_KEY), any(), any(Duration.class))).thenReturn(true);
        request.setCurrency(CurrencyType.EUR);

        assertThrows(CurrencyMismatchException.class, () -> transferService.createTransfer(request, jwt));

        verify(redisTemplate).delete(REDIS_KEY);
        verify(transferRepository, never()).saveAndFlush(any());
        verify(accountTransferClient, never()).internalTransfer(any());
    }

    @Test
    void createTransfer_WhenAuditLogFails_ThenTransferStillSucceeds() {
        when(valueOps.setIfAbsent(eq(REDIS_KEY), any(), any(Duration.class))).thenReturn(true);
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("mongo down"));

        Transaction result = transferService.createTransfer(request, jwt);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.DONE);
    }

    @Test
    void createTransfer_KeysAreScopedPerUser() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        transferService.createTransfer(request, jwt);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(key.capture(), any(), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("idempotency:" + USER_ID + ":" + KEY);
    }
}
