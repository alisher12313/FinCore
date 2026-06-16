package com.pm.transactionservice.entity.nosql;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLog {
    @Id
    private String id;

    private String transferId;
    private EventType eventType;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String initiatedBy;

    @CreatedDate
    private LocalDateTime createdAt;
}
