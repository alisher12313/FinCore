package com.pm.transactionservice.repository.sql;

import com.pm.transactionservice.entity.sql.Transaction;
import com.pm.transactionservice.entity.sql.TransactionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransferSpecification {

    public static Specification<Transaction> filter(
            UUID initiatedBy,
            UUID toAccountId,
            TransactionStatus status,
            BigDecimal minAmount,
            LocalDateTime createdAfter) {

        return Specification
                .where(initiatedBy(initiatedBy, toAccountId))
                .and(hasStatus(status))
                .and(amountGreaterThan(minAmount))
                .and(createdAfter(createdAfter));
    }

    public static Specification<Transaction> initiatedBy(UUID initiatedBy, UUID accountId) {
        return ((root, query, criteriaBuilder) ->
                    initiatedBy == null ? null : criteriaBuilder.or(criteriaBuilder.equal(root.get("initiatedBy"), initiatedBy), criteriaBuilder.equal(root.get("toAccountId"), accountId))
                );
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return ((root, query, criteriaBuilder) ->
                status == null ? null : criteriaBuilder.equal(root.get("hasStatus"), status)
                );
    }

    private static Specification<Transaction> amountGreaterThan(BigDecimal amount) {
        return (root, query, cb) ->
                amount == null ? null : cb.greaterThanOrEqualTo(root.get("amount"), amount);
    }

    private static Specification<Transaction> createdAfter(LocalDateTime date) {
        return (root, query, cb) ->
                date == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }
}
