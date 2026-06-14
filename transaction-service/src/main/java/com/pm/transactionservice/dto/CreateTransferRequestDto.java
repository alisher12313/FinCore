package com.pm.transactionservice.dto;

import com.pm.transactionservice.entity.CurrencyType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreateTransferRequestDto {
    private String fromAccountId;
    private String toAccountId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private CurrencyType currency;
    private String idempotencyKey;
}
