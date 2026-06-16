package com.pm.transactionservice.repository.nosql;

import com.pm.transactionservice.entity.nosql.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}
