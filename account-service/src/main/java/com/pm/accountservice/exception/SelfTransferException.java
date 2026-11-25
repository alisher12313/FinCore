package com.pm.accountservice.exception;

public class SelfTransferException extends RuntimeException {
    public SelfTransferException(String accountNumber) {
        super("Cannot transfer to the same account: " + accountNumber);
    }
}
