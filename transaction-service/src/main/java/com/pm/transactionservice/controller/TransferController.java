package com.pm.transactionservice.controller;

import com.pm.transactionservice.dto.CreateTransferRequestDto;
import com.pm.transactionservice.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<?> createTransfer(@Valid @RequestBody CreateTransferRequestDto dto) {
        return ResponseEntity.ok(transferService.createTransfer(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransfer(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        return ResponseEntity.ok(transferService.getHistory());
    }
}
