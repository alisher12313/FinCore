package com.pm.accountservice.controller;

import com.pm.accountservice.dto.*;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.View;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateAccountRequestDto accountRequest, @AuthenticationPrincipal Jwt jwt) {
        log.info("Creating account");

        Account account = accountService.createAccount(accountRequest, jwt);
        AccountResponseDto accountResponseDto = accountService.toDto(account);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Account created successfully",
                "result", accountResponseDto
            )
        );
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        log.info("Getting my profile");

        Account account = accountService.getMyProfile(jwt);
        AccountResponseDto dto = accountService.toDto(account);

        return ResponseEntity.ok(Map.of(
                "message", "Account fetched successfully",
                "result", dto
            )
        );
    }

    @GetMapping("/my/balance/convert")
    public ResponseEntity<?> getConvertedBalance(@RequestParam CurrencyType currency, @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting converted balance");

        BigDecimal converted = accountService.getConvertedBalance(currency, jwt);

        return ResponseEntity.ok(Map.of(
                "message", "Balance converted successfully",
                "result", converted
            )
        );
    }

    @PostMapping("/my/balance")
    public ResponseEntity<?> topUpBalance(@Valid @RequestBody TopUpRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
        log.info("Top up balance");

        Account account = accountService.topUpBalance(dto, jwt);
        AccountResponseDto accountResponseDto = accountService.toDto(account);

        return ResponseEntity.ok(Map.of(
                "message", "Account fetched successfully",
                "result", accountResponseDto
            )
        );
    }

    @PatchMapping("/{id}/freeze")
    public ResponseEntity<?> freezeAccount(@PathVariable("id") UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        log.info("Freezing account {}", accountId);

        Account account = accountService.freezeAccount(accountId, jwt);
        AccountResponseDto accountResponseDto = accountService.toDto(account);

        return ResponseEntity.ok(Map.of(
                "message", "Account frozen successfully",
                "result", accountResponseDto
            )
        );
    }

    @PatchMapping("/{id}/unfreeze")
    public ResponseEntity<?> unfreezeAccount(@PathVariable("id") UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        log.info("Unfreezing account {}", accountId);

        Account account = accountService.unfreezeAccount(accountId, jwt);
        AccountResponseDto accountResponseDto = accountService.toDto(account);

        return ResponseEntity.ok(Map.of(
                        "message", "Account unfrozen successfully",
                        "result", accountResponseDto
                )
        );
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<?> getBalance(@PathVariable("id") String accountId) {
        log.info("Getting balance for account {}", accountId);

        BalanceViewProjection viewProjection = accountService.getBalanceInternal(accountId);

        return ResponseEntity.ok(
                Map.of("result", viewProjection)
        );
    }

    @PostMapping("/internal/transfer")
    public ResponseEntity<?> internalTransfer(@Valid @RequestBody InternalTransferRequestDto dto) {
        accountService.internalTransfer(
                dto
        );
        return ResponseEntity.ok(Map.of("message", "Transfer completed successfully"));
    }
}
