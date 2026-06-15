package com.pm.transactionservice.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String key) {
        super("Transfer already processed with key: " + key);
    }
}