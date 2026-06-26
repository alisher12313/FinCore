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
import com.pm.transactionservice.exception.TransferFailedException;
import com.pm.transactionservice.exception.TransferNotFoundException;
import com.pm.transactionservice.mapper.TransferMapper;
import com.pm.transactionservice.repository.nosql.AuditLogRepository;
import com.pm.transactionservice.repository.sql.TransferRepository;
import com.pm.transactionservice.repository.sql.TransferSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
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

    private final TransferRepository transferRepository;
    private final AccountTransferClient accountTransferClient;
    private final TransferMapper transferMapper;
    private final AuditLogRepository auditLogRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.kafka-topics.topic-transaction-complete}")
    private String topicTransactionComplete;

    @Transactional
    public Transaction createTransfer(CreateTransferRequestDto dto, Jwt jwt) {
        UUID fromUserId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email");
        String idempotencyKey = String.format("transaction:%s", dto.getIdempotencyKey());

        if(Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey))) {
            return (Transaction) redisTemplate.opsForValue().get(idempotencyKey);
        }

        //replaced by redis
//        Optional<Transaction> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
//        if (existing.isPresent()) {
//            return existing.get();
//        }

        AccountResponseDto from = accountTransferClient.getAccountByUserId(UUID.fromString(jwt.getSubject())).getBody();
        AccountResponseDto to = accountTransferClient.getAccountByAccountNumber(dto.getToAccountNumber()).getBody();
        UUID fromAccountId = Optional.ofNullable(from.getId()).orElseThrow();
        UUID toAccountId = Optional.of(to.getId()).orElseThrow();

        Transaction transaction = Transaction.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(dto.getIdempotencyKey())
                .initiatedBy(fromUserId)
                .build();

        try {
            accountTransferClient.internalTransfer(
                    new InternalTransferRequestDto(
                            from.getAccountNumber(),
                            dto.getToAccountNumber(),
                            dto.getAmount()
                    )
            );
            transaction.setStatus(TransactionStatus.DONE);
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transferRepository.save(transaction);
            throw new TransferFailedException("Transfer failed: " + e.getMessage());
        }

        transaction = transferRepository.save(transaction);

        redisTemplate.opsForValue().set(idempotencyKey, transaction, Duration.ofDays(1));

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

        return transaction;
    }

    public Transaction getTransfer(UUID id, Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());

        AccountResponseDto account =
                Optional.ofNullable(
                        accountTransferClient
                                .getAccountByUserId(userId)
                                .getBody()
                ).orElseThrow();

        return transferRepository
                .findAccessibleTransfer(
                        id,
                        userId,
                        account.getId()
                )
                .orElseThrow(() ->
                        new TransferNotFoundException(
                                "Transfer not found: " + id));
    }

    public Page<TransferResponseDto> getHistory(
            TransactionStatus status,
            BigDecimal minAmount,
            LocalDateTime createdAfter,
            Pageable pageable,
            Jwt jwt
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        AccountResponseDto account =
                Optional.ofNullable(
                        accountTransferClient
                                .getAccountByUserId(userId)
                                .getBody()
                ).orElseThrow();

        return transferRepository.findAll(
                TransferSpecification.filter(
                        userId,
                        account.getId(),
                        status,
                        minAmount,
                        createdAfter
                ),
                pageable
        ).map(transferMapper::toDto);
    }
}
