package com.pm.accountservice.dto;

import com.pm.accountservice.entity.CurrencyType;

import java.math.BigDecimal;

public interface BalanceViewProjection {
    BigDecimal getBalance();
    CurrencyType getCurrency();
}
