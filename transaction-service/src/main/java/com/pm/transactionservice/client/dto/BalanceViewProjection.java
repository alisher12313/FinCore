package com.pm.transactionservice.client.dto;

import com.pm.transactionservice.entity.sql.CurrencyType;

import java.math.BigDecimal;

public interface BalanceViewProjection {
    BigDecimal getBalance();
    CurrencyType getCurrency();
}
