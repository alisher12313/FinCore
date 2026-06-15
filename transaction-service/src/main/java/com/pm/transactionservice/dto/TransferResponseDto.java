package com.pm.transactionservice.dto;

import com.pm.transactionservice.entity.CurrencyType;
import com.pm.transactionservice.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferResponseDto(
        UUID id,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        CurrencyType currency,
        TransactionStatus status,
        UUID initiatedBy,
        LocalDateTime createdAt
) {
}
