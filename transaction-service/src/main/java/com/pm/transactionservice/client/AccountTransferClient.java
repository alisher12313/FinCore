package com.pm.transactionservice.client;

import com.pm.transactionservice.client.dto.BalanceViewProjection;
import com.pm.transactionservice.client.dto.InternalTransferRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountTransferClient {

    @GetMapping("/internal/{accountNumber}/balance")
    BalanceViewProjection getBalance(@PathVariable String accountNumber);

    @PostMapping("/internal/transfer")
    void internalTransfer(@RequestBody InternalTransferRequestDto dto);
}
