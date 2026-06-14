package com.pm.transactionservice.dto;

import com.pm.transactionservice.entity.CurrencyType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@ToString
public class CreateTransferRequestDto {
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private CurrencyType currency;
    private String idempotencyKey;
}
