package com.pm.accountservice.controller;

import com.pm.accountservice.dto.AccountResponseDto;
import com.pm.accountservice.dto.CreateAccountRequestDto;
import com.pm.accountservice.dto.ViewOrChangeCurrencyDto;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateAccountRequestDto accountRequest) {
        log.info("Creating account");

        Account account = accountService.createAccount(accountRequest);
        AccountResponseDto accountResponseDto = accountService.toDto(account);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Account created successfully",
                "result", accountResponseDto
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyProfile() {
        log.info("Getting my profile");

        Account account = accountService.getMyProfile();
        AccountResponseDto dto = accountService.toDto(account);

        return ResponseEntity.ok(Map.of(
                "message", "Account fetched successfully",
                "result", dto
        ));
    }

    @GetMapping("/my/balance/convert")
    public ResponseEntity<?> getConvertedBalance(@Valid @RequestBody ViewOrChangeCurrencyDto dto) {
        log.info("Getting converted balance");

        BigDecimal converted = accountService.getConvertedBalance(dto);

        return ResponseEntity.ok(Map.of(
                "message", "Balance converted successfully",
                "result", converted
        ));
    }
}
