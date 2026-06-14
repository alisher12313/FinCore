package com.pm.transactionservice.service;

import com.pm.transactionservice.client.AccountTransferClient;
import com.pm.transactionservice.client.dto.InternalTransferRequestDto;
import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.entity.Transaction;
import com.pm.transactionservice.entity.TransactionStatus;
import com.pm.transactionservice.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountTransferClient accountTransferClient;

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
            throw new RuntimeException("Transfer failed: " + e.getMessage());
        }

        transaction.setStatus(TransactionStatus.DONE);

        return transferRepository.save(transaction);
    }

    public Transaction getTransfer(UUID id) {
        return transferRepository.findById(id).orElseThrow(() -> new RuntimeException("Transfer not found: " + id));
    }

    public List<Transaction> getHistory() {
        UUID dummyUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return transferRepository.findByInitiatedBy(dummyUserId);
    }
}
