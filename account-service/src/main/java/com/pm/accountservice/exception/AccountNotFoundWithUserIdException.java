package com.pm.accountservice.exception;

public class AccountNotFoundWithUserIdException extends RuntimeException {
    public AccountNotFoundWithUserIdException(String userId) {
        super("Account with id " + userId + " not found");
    }
}
