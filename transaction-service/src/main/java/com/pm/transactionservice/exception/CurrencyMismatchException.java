package com.pm.transactionservice.exception;

import com.pm.transactionservice.entity.sql.CurrencyType;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(CurrencyType requested, CurrencyType accountCurrency) {
        super("Transfer currency " + requested + " does not match your account currency " + accountCurrency
                + ". Amounts are sent in the sender's account currency.");
    }
}
