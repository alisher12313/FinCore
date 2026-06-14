package com.pm.accountservice.exception;

import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(UUID userId) {
        super("Insufficient balance for " + userId);
    }
}
