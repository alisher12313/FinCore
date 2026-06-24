package com.pm.transactionservice.dto;

import com.pm.transactionservice.entity.sql.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferIdempotencyDto {

    private UUID transactionId;

    private TransactionStatus status;
}
