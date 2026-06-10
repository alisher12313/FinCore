package com.pm.accountservice.exception;

import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
           errors.put(error.getObjectName(), error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(
                Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.BAD_REQUEST.toString(),
                        "status code", HttpStatus.BAD_REQUEST.value(),
                        "message", "Validation Failed",
                        "errors", errors)
        );
    }

    @ExceptionHandler(AccountNotFoundWithUserIdException.class)
    public ResponseEntity<?> handleAccountNotFoundWithUserIdException(AccountNotFoundWithUserIdException ex) {
       return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
               Map.of(
                       "error", ex.getMessage()
               )
       );
    }

    @ExceptionHandler(AccountFrozenException.class)
    public ResponseEntity<?> handleAccountFrozenException(AccountNotFoundWithUserIdException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of(
                        "error", ex.getMessage()
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidJsonOrEnum(HttpMessageNotReadableException ex) {
        return Map.of(
                "timestamp", LocalDateTime.now(),
                "status", 400,
                "message", "Invalid request body. Check currency value or JSON format"
        );
    }
}
