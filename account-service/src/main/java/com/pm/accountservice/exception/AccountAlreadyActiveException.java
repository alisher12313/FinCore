package com.pm.accountservice.exception;

public class AccountAlreadyActiveException extends RuntimeException {
    public AccountAlreadyActiveException(String userId) {
        super("Account is active and cannot perform actions! User ID: " + userId);
    }
}
