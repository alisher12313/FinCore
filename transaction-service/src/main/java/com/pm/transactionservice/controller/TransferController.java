package com.pm.transactionservice.controller;

import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.entity.Transaction;
import com.pm.transactionservice.entity.TransactionStatus;
import com.pm.transactionservice.mapper.TransferMapper;
import com.pm.transactionservice.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> createTransfer(@Valid @RequestBody CreateTransferRequestDto dto) {
        Transaction transaction = transferService.createTransfer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Transfer created successfully",
                "result", transferMapper.toDto(transaction)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransfer(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) LocalDateTime createdAfter) {

        return ResponseEntity.ok(transferService.getHistory(status, minAmount, createdAfter, pageable));
    }
}
