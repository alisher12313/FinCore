package com.pm.accountservice.service;

import com.pm.accountservice.client.CurrencyClientApi;
import com.pm.accountservice.dto.InternalTransferRequestDto;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.events.KafkaEventPublisher;
import com.pm.accountservice.events.KafkaTopics;
import com.pm.accountservice.exception.InsufficientBalanceException;
import com.pm.accountservice.mapper.AccountMapper;
import com.pm.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs AccountService against a real PostgreSQL (Testcontainers + Liquibase migrations)
 * to prove the row locking works. Needs Docker running.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AccountService.class, AccountServiceIntegrationTest.CacheConfig.class})
@TestPropertySource(properties = {"currency.api=test-key", "currency.url=localhost"})
// Each service call must commit on its own, like in production; the default
// "wrap every test in one rolled-back transaction" would hide the concurrency.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @TestConfiguration
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("accounts");
        }
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private AccountMapper accountMapper;
    @MockitoBean
    private CurrencyClientApi currencyClientApi;
    @MockitoBean
    private KafkaEventPublisher kafkaEventPublisher;
    @MockitoBean
    private KafkaTopics kafkaTopics;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
    }

    private Account saveAccount(String number, String balance) {
        return accountRepository.save(Account.builder()
                .userId(UUID.randomUUID())
                .accountNumber(number)
                .balance(new BigDecimal(balance))
                .currency(CurrencyType.KZT)
                .status(AccountStatus.ACTIVE)
                .email(number + "@example.com")
                .build());
    }

    private BigDecimal balanceOf(String number) {
        return accountRepository.findByAccountNumber(number).orElseThrow().getBalance();
    }

    @Test
    void concurrentTransfers_FromSameAccount_NeverOverspend() throws Exception {
        saveAccount("KZ0000000000000001", "100.00");
        saveAccount("KZ0000000000000002", "0.00");

        int threads = 20;            // 20 transfers of 10 each = 200 requested, only 100 available
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    accountService.internalTransfer(new InternalTransferRequestDto(
                            "KZ0000000000000001", "KZ0000000000000002", new BigDecimal("10")));
                    succeeded.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();               // release all threads at once
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS); // rethrows any unexpected exception
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);
        assertThat(balanceOf("KZ0000000000000001")).isEqualByComparingTo("0");
        assertThat(balanceOf("KZ0000000000000002")).isEqualByComparingTo("100");
    }

    @Test
    void opposingTransfers_AtTheSameTime_DoNotDeadlock() throws Exception {
        saveAccount("KZ0000000000000001", "1000.00");
        saveAccount("KZ0000000000000002", "1000.00");

        int perDirection = 10;
        ExecutorService pool = Executors.newFixedThreadPool(perDirection * 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < perDirection; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                accountService.internalTransfer(new InternalTransferRequestDto(
                        "KZ0000000000000001", "KZ0000000000000002", new BigDecimal("5")));
                return null;
            }));
            futures.add(pool.submit(() -> {
                start.await();
                accountService.internalTransfer(new InternalTransferRequestDto(
                        "KZ0000000000000002", "KZ0000000000000001", new BigDecimal("5")));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS); // a deadlock would surface here as an exception
        }
        pool.shutdown();

        // same amount both ways -> balances unchanged, total money conserved
        assertThat(balanceOf("KZ0000000000000001")).isEqualByComparingTo("1000");
        assertThat(balanceOf("KZ0000000000000002")).isEqualByComparingTo("1000");
    }
}
