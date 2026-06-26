package com.pm.transactionservice.repository.sql;

import com.pm.transactionservice.entity.sql.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<Transaction> findByInitiatedBy(UUID initiatedBy);

    @Query("""
    SELECT t
    FROM Transaction t
    WHERE t.id = :transferId
      AND (
            t.initiatedBy = :userId
            OR
            t.toAccountId = :accountId
      )
""")
    Optional<Transaction> findAccessibleTransfer(
            @Param("transferId") UUID transferId,
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId
    );
}
