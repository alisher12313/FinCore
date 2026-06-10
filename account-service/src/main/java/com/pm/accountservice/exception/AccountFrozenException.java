package com.pm.accountservice.exception;

public class AccountFrozenException extends RuntimeException {
    public AccountFrozenException(String userId) {
        super("Account is frozen and cannot perform actions! User ID: " + userId);
    }
}
