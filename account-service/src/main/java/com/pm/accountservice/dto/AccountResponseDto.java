package com.pm.accountservice.dto;

import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
public class AccountResponseDto {
    private UUID id;
    private UUID userId;
    private String accountNumber;
    private BigDecimal balance;
    private CurrencyType currency;
    private AccountStatus status;
    private LocalDateTime createdAt;
}
