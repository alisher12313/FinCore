package com.pm.accountservice.dto;

import com.pm.accountservice.entity.CurrencyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class TopUpRequestDto {

    @NotNull(message = "Please specify top up amount!")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Please select currency type!")
    private CurrencyType currencyType;
}
