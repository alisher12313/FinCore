package com.pm.transactionservice.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String key) {
        super("A transfer with idempotency key " + key + " is already being processed");
    }
}