package com.pm.transactionservice.controller;

import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.entity.sql.Transaction;
import com.pm.transactionservice.entity.sql.TransactionStatus;
import com.pm.transactionservice.mapper.TransferMapper;
import com.pm.transactionservice.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final TransferMapper transferMapper;

    @PostMapping
    public ResponseEntity<?> createTransfer(@Valid @RequestBody CreateTransferRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
        Transaction transaction = transferService.createTransfer(dto, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Transfer created successfully",
                "result", transferMapper.toDto(transaction)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransfer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transferService.getTransfer(id, jwt));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) LocalDateTime createdAfter,
            @AuthenticationPrincipal Jwt jwt
    ) {

        return ResponseEntity.ok(transferService.getHistory(status, minAmount, createdAfter, pageable, jwt));
    }
}
