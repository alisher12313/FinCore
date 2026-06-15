package com.pm.transactionservice.entity.nosql;

import jakarta.persistence.Id;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "audit_logs")
public class AuditLog {
    @Id
    private ObjectId _id;

    private String transferId;
    private EventType eventType;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String initiatedBy;
    private LocalDateTime createdAt;
}
