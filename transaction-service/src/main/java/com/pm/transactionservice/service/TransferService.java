package com.pm.transactionservice.service;

import com.pm.transactionservice.client.AccountTransferClient;
import com.pm.transactionservice.client.dto.AccountResponseDto;
import com.pm.transactionservice.client.dto.InternalTransferRequestDto;
import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.dto.TransferResponseDto;
import com.pm.transactionservice.entity.nosql.AuditLog;
import com.pm.transactionservice.entity.nosql.EventType;
import com.pm.transactionservice.entity.sql.Transaction;
import com.pm.transactionservice.entity.sql.TransactionStatus;
import com.pm.transactionservice.events.KafkaEventPublisher;
import com.pm.transactionservice.events.TransferCompletedEvent;
import com.pm.transactionservice.exception.CurrencyMismatchException;
import com.pm.transactionservice.exception.DuplicateIdempotencyKeyException;
import com.pm.transactionservice.exception.TransferFailedException;
import com.pm.transactionservice.exception.TransferNotFoundException;
import com.pm.transactionservice.mapper.TransferMapper;
import com.pm.transactionservice.repository.nosql.AuditLogRepository;
import com.pm.transactionservice.repository.sql.TransferRepository;
import com.pm.transactionservice.repository.sql.TransferSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TransferService {

    static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration IN_PROGRESS_TTL = Duration.ofMinutes(5);
    private static final Duration RESULT_TTL = Duration.ofDays(1);

    private final TransferRepository transferRepository;
    private final AccountTransferClient accountTransferClient;
    private final TransferMapper transferMapper;
    private final AuditLogRepository auditLogRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.kafka-topics.topic-transaction-complete}")
    private String topicTransactionComplete;

    /**
     * Flow:
     *  1. Idempotency key is scoped per user ("userId:key"), so one user can't read another's transfer.
     *  2. Fast path: Redis holds the transfer id of an already processed key.
     *  3. The key is claimed atomically in Redis (SET NX) BEFORE any money moves,
     *     so two concurrent requests with the same key can't both transfer.
     *  4. A PENDING row is inserted; the unique constraint on idempotency_key is the
     *     durable guard if Redis was flushed.
     *  5. The money moves (Feign), and the row is updated to DONE or FAILED.
     *
     * No surrounding DB transaction: each save commits immediately. A transaction held
     * open across a remote call would also roll back the FAILED record on error.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transaction createTransfer(CreateTransferRequestDto dto, Jwt jwt) {
        UUID fromUserId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email");
        String scopedKey = fromUserId + ":" + dto.getIdempotencyKey();
        String redisKey = "idempotency:" + scopedKey;

        Optional<Transaction> alreadyProcessed = findProcessed(redisKey, scopedKey);
        if (alreadyProcessed.isPresent()) {
            return alreadyProcessed.get();
        }

        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, IN_PROGRESS_TTL);
        if (!Boolean.TRUE.equals(claimed)) {
            // another request with the same key is running (or just finished)
            return findProcessed(redisKey, scopedKey)
                    .orElseThrow(() -> new DuplicateIdempotencyKeyException(dto.getIdempotencyKey()));
        }

        AccountResponseDto from;
        AccountResponseDto to;
        try {
            from = Optional.ofNullable(accountTransferClient.getAccountByUserId(fromUserId).getBody())
                    .orElseThrow(() -> new TransferFailedException("Sender account not found"));
            to = Optional.ofNullable(accountTransferClient.getAccountByAccountNumber(dto.getToAccountNumber()).getBody())
                    .orElseThrow(() -> new TransferFailedException("Receiver account not found"));

            if (from.getCurrency() != dto.getCurrency()) {
                throw new CurrencyMismatchException(dto.getCurrency(), from.getCurrency());
            }
        } catch (RuntimeException e) {
            // nothing has been written or moved yet: release the key so the client can retry
            redisTemplate.delete(redisKey);
            throw e;
        }

        Transaction transaction = Transaction.builder()
                .fromAccountId(from.getId())
                .toAccountId(to.getId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(scopedKey)
                .initiatedBy(fromUserId)
                .build();

        try {
            transaction = transferRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // Redis lost the key, but the DB already has this transfer
            return transferRepository.findByIdempotencyKey(scopedKey)
                    .orElseThrow(() -> new DuplicateIdempotencyKeyException(dto.getIdempotencyKey()));
        }

        try {
            accountTransferClient.internalTransfer(
                    new InternalTransferRequestDto(from.getAccountNumber(), dto.getToAccountNumber(), dto.getAmount()));
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transferRepository.save(transaction);
            redisTemplate.opsForValue().set(redisKey, transaction.getId().toString(), RESULT_TTL);
            throw new TransferFailedException("Transfer failed: " + e.getMessage());
        }

        transaction.setStatus(TransactionStatus.DONE);
        transaction = transferRepository.save(transaction);
        redisTemplate.opsForValue().set(redisKey, transaction.getId().toString(), RESULT_TTL);

        // The money has moved; audit/notification failures must not turn this into an error
        // response (the client would retry). A transactional outbox would make these guaranteed.
        try {
            saveAuditLog(transaction);
            publishCompletedEvent(transaction, email);
        } catch (Exception e) {
            log.error("Post-transfer side effects failed for transfer {}: {}", transaction.getId(), e.getMessage());
        }

        return transaction;
    }

    private Optional<Transaction> findProcessed(String redisKey, String scopedKey) {
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof String id && !IN_PROGRESS.equals(id)) {
            return transferRepository.findById(UUID.fromString(id));
        }
        if (cached == null) {
            return transferRepository.findByIdempotencyKey(scopedKey)
                    .filter(t -> t.getStatus() != TransactionStatus.PENDING);
        }
        return Optional.empty();
    }

    private void saveAuditLog(Transaction transaction) {
        AuditLog auditLog = AuditLog.builder()
                .transferId(transaction.getId().toString())
                .eventType(EventType.TRANSFER_DONE)
                .fromAccountId(transaction.getFromAccountId().toString())
                .toAccountId(transaction.getToAccountId().toString())
                .amount(transaction.getAmount())
                .initiatedBy(transaction.getInitiatedBy().toString())
                .build();

        auditLogRepository.save(auditLog);
        log.info("AuditLog saved with id: {}", auditLog.getId());
    }

    private void publishCompletedEvent(Transaction transaction, String email) {
        TransferCompletedEvent event = TransferCompletedEvent.builder()
                .transferId(transaction.getId())
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .initiatedBy(transaction.getInitiatedBy())
                .completedAt(Instant.now())
                .email(email)
                .build();

        kafkaEventPublisher.publish(topicTransactionComplete, transaction.getId().toString(), event);
    }

    public TransferResponseDto getTransfer(UUID id, Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        AccountResponseDto account = Optional.ofNullable(accountTransferClient.getAccountByUserId(userId).getBody())
                .orElseThrow(() -> new TransferNotFoundException("Account not found for user: " + userId));

        return transferRepository.findAccessibleTransfer(id, userId, account.getId())
                .map(transferMapper::toDto)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + id));
    }

    public Page<TransferResponseDto> getHistory(
            TransactionStatus status,
            BigDecimal minAmount,
            LocalDateTime createdAfter,
            Pageable pageable,
            Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        AccountResponseDto account = Optional.ofNullable(accountTransferClient.getAccountByUserId(userId).getBody())
                .orElseThrow(() -> new TransferNotFoundException("Account not found for user: " + userId));

        return transferRepository.findAll(
                TransferSpecification.filter(userId, account.getId(), status, minAmount, createdAfter),
                pageable
        ).map(transferMapper::toDto);
    }
}
