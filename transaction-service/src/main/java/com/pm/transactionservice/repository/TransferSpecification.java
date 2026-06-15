package com.pm.transactionservice.repository;

import com.pm.transactionservice.entity.CurrencyType;
import com.pm.transactionservice.entity.Transaction;
import com.pm.transactionservice.entity.TransactionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransferSpecification {

    public static Specification<Transaction> filter(
            UUID initiatedBy,
            TransactionStatus status,
            BigDecimal minAmount,
            LocalDateTime createdAfter) {

        return Specification
                .where(initiatedBy(initiatedBy))
                .and(hasStatus(status))
                .and(amountGreaterThan(minAmount))
                .and(createdAfter(createdAfter));
    }

    public static Specification<Transaction> initiatedBy(UUID initiatedBy) {
        return ((root, query, criteriaBuilder) ->
                    initiatedBy == null ? null : criteriaBuilder.equal(root.get("initiatedBy"), initiatedBy)
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
