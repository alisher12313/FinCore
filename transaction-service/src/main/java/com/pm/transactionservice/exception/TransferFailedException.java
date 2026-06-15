package com.pm.transactionservice.exception;

public class TransferFailedException extends RuntimeException {
    public TransferFailedException(String message) {
        super("Transfer failed: " + message);
    }
}