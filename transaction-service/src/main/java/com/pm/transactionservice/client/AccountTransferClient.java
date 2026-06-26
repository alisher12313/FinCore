package com.pm.transactionservice.client;

import com.pm.transactionservice.client.configuration.OpenfeignConfiguration;
import com.pm.transactionservice.client.dto.AccountResponseDto;
import com.pm.transactionservice.client.dto.BalanceViewProjection;
import com.pm.transactionservice.client.dto.InternalTransferRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "account-service", url = "${account.service.url}", configuration = OpenfeignConfiguration.class)
public interface AccountTransferClient {

    @GetMapping("/internal/{accountNumber}/balance")
    ResponseEntity<BalanceViewProjection> getBalance(@PathVariable String accountNumber);

    @GetMapping("/internal/{id}")
    ResponseEntity<AccountResponseDto> getAccountByAccountNumber(@PathVariable("id") String accountNumber);

    @GetMapping("/internal/users/{userId}/account")
    ResponseEntity<AccountResponseDto> getAccountByUserId(
            @PathVariable UUID userId);

    @PostMapping("/internal/transfer")
    void internalTransfer(@RequestBody InternalTransferRequestDto dto);
}
