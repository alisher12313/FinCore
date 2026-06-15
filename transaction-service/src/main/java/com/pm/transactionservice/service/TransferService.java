package com.pm.transactionservice.service;

import com.pm.transactionservice.client.AccountTransferClient;
import com.pm.transactionservice.client.dto.InternalTransferRequestDto;
import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.dto.TransferResponseDto;
import com.pm.transactionservice.entity.sql.Transaction;
import com.pm.transactionservice.entity.sql.TransactionStatus;
import com.pm.transactionservice.exception.TransferFailedException;
import com.pm.transactionservice.exception.TransferNotFoundException;
import com.pm.transactionservice.mapper.TransferMapper;
import com.pm.transactionservice.repository.TransferRepository;
import com.pm.transactionservice.repository.TransferSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountTransferClient accountTransferClient;
    private final TransferMapper transferMapper;

    // 1. check idempotency key
    // 2. build and save transfer with status PENDING
    // 3. (later) call Account Service via Feign
    // 4. update status to DONE
    // 5. return
    @Transactional
    public Transaction createTransfer(CreateTransferRequestDto dto) {
        UUID dummyUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String idempotencyKey = dto.getIdempotencyKey();

        Optional<Transaction> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Transaction transaction = Transaction.builder()
                .fromAccountId(UUID.fromString(dto.getFromAccountId()))
                .toAccountId(UUID.fromString(dto.getToAccountId()))
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(dto.getIdempotencyKey())
                .initiatedBy(dummyUserId)
                .build();

        //After feign call to Account service
        try {
            accountTransferClient.internalTransfer(
                    new InternalTransferRequestDto(
                            dto.getFromAccountNumber(),
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

        return transferRepository.save(transaction);
    }

    public Transaction getTransfer(UUID id) {
        return transferRepository.findById(id).orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + id));
    }

    public Page<TransferResponseDto> getHistory(
            TransactionStatus status,
            BigDecimal minAmount,
            LocalDateTime createdAfter,
            Pageable pageable) {

        UUID dummyUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        return transferRepository.findAll(
                TransferSpecification.filter(dummyUserId, status, minAmount, createdAfter),
                pageable
        ).map(transferMapper::toDto);
    }
}
