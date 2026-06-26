package com.pm.transactionservice.dto;

import com.pm.transactionservice.entity.sql.CurrencyType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreateTransferRequestDto {
    @NotBlank(message = "To account number is required")
    private String toAccountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private CurrencyType currency;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
