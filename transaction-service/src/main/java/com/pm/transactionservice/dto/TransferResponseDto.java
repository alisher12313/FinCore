package com.pm.transactionservice.dto;

import com.pm.transactionservice.entity.sql.CurrencyType;
import com.pm.transactionservice.entity.sql.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferResponseDto(
        UUID id,
        BigDecimal amount,
        CurrencyType currency,
        TransactionStatus status,
        UUID initiatedBy,
        LocalDateTime createdAt
) {
}
