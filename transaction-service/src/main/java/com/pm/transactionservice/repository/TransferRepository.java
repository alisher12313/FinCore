package com.pm.transactionservice.repository;

import com.pm.transactionservice.entity.sql.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<Transaction> findByInitiatedBy(UUID initiatedBy);
//    List<Transaction> findByInitiatedByOrToAccountId(UUID initiatedBy, UUID toAccountId);

}
