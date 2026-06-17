package com.pm.transactionservice.events;

import com.pm.transactionservice.entity.sql.CurrencyType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferCompletedEvent {
    private UUID transferId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private CurrencyType currency;
    private UUID initiatedBy;
    private Instant completedAt;
}
