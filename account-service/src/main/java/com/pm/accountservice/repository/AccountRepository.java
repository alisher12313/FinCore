package com.pm.accountservice.repository;

import com.pm.accountservice.dto.BalanceViewProjection;
import com.pm.accountservice.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByUserId(UUID userId);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<BalanceViewProjection> findBalanceByUserId(UUID userId);
    Optional<BalanceViewProjection> findBalanceByAccountNumber(String accountNumber);

    // SELECT ... FOR UPDATE: other transactions that want to lock or modify this row
    // wait until the current transaction commits. Prevents lost updates / double spending.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") UUID userId);
}
