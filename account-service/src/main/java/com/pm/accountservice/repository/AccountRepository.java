package com.pm.accountservice.repository;

import com.pm.accountservice.dto.BalanceViewProjection;
import com.pm.accountservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByUserId(UUID userId);
    Optional<BalanceViewProjection> findBalanceByUserId(UUID userId);
    Optional<BalanceViewProjection> findBalanceByAccountNumber(String accountNumber);
}
