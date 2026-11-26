package com.pm.accountservice.service;

import com.pm.accountservice.client.CurrencyClientApi;
import com.pm.accountservice.dto.*;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.events.AccountFreezeEvent;
import com.pm.accountservice.events.AccountUnfreezeEvent;
import com.pm.accountservice.events.KafkaEventPublisher;
import com.pm.accountservice.events.KafkaTopics;
import com.pm.accountservice.exception.AccountAlreadyActiveException;
import com.pm.accountservice.exception.AccountFrozenException;
import com.pm.accountservice.exception.AccountNotFoundWithUserIdException;
import com.pm.accountservice.exception.InsufficientBalanceException;
import com.pm.accountservice.exception.SelfTransferException;
import com.pm.accountservice.mapper.AccountMapper;
import com.pm.accountservice.repository.AccountRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
@Validated
//Later add email and phone number
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final CurrencyClientApi currencyClientApi;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final KafkaTopics kafkaTopics;
    private final CacheManager cacheManager;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ACCOUNTS_CACHE = "accounts";

    @Value("${currency.api}")
    private String apiKey;

    public Account createAccount(CreateAccountRequestDto accountRequest, Jwt jwt) {
        log.info("Creating account {}", accountRequest.toString());
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email");
        String accountNumber = generateAccountNumber();
        BigDecimal balance = new BigDecimal(0);
        CurrencyType currencyType = accountRequest.getCurrency();
        AccountStatus status = AccountStatus.ACTIVE;

        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .balance(balance)
                .currency(currencyType)
                .status(status)
                .email(email)
                .build();

        return accountRepository.save(account);
    }

    @Cacheable(value = "accounts", key = "#jwt.subject")
    public Account getMyProfile(Jwt jwt){
        UUID userId = UUID.fromString(jwt.getSubject());

        return accountRepository.findByUserId(userId).orElseThrow(() -> new AccountNotFoundWithUserIdException(userId.toString()));
    }

    public BigDecimal getConvertedBalance(@NotNull(message = "Currency must be selected!") CurrencyType type, Jwt jwt) {
        UUID dummyId = UUID.fromString(jwt.getSubject());

        BalanceViewProjection balance = accountRepository.findBalanceByUserId(dummyId).orElseThrow(() -> new AccountNotFoundWithUserIdException(dummyId.toString()));
        String targetCurrency = type.name();

        CurrencyApiResponse currencyApiResponse = currencyClientApi.convert(
            apiKey,
            balance.getCurrency().name(),
            targetCurrency
        );

        BigDecimal rate = currencyApiResponse.getData().get(targetCurrency).getValue();

        return balance.getBalance().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @CacheEvict(value = "accounts", key = "#jwt.subject")
    public Account topUpBalance(TopUpRequestDto topUpRequestDto, Jwt jwt) {
        UUID dummyId = UUID.fromString(jwt.getSubject());

        Account account = accountRepository.findByUserIdForUpdate(dummyId).orElseThrow(() -> new AccountNotFoundWithUserIdException(dummyId.toString()));
        String currencyType = topUpRequestDto.getCurrencyType().name();
        BigDecimal amount = topUpRequestDto.getAmount();

        String accountCurrency = account.getCurrency().name();

        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(dummyId.toString());
        }

        BigDecimal amountToAdd = convertAmount(amount, currencyType, accountCurrency);
        account.setBalance(account.getBalance().add(amountToAdd).setScale(2, RoundingMode.HALF_UP));

        return accountRepository.save(account);
    }

    public Account freezeAccount(UUID accountId, Jwt jwt) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundWithUserIdException(accountId.toString()));

        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(accountId.toString());
        }

        account.setStatus(AccountStatus.FROZEN);
        Account savedAccount = accountRepository.save(account);
        evictProfileCache(account.getUserId());

        AccountFreezeEvent event = AccountFreezeEvent.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .freezeTime(Instant.now())
                .userId(account.getUserId())
                .email(account.getEmail())
                .build();

        kafkaEventPublisher.publish(kafkaTopics.getTopicAccountStatusChanged(), accountId.toString(), event);
        return savedAccount;
    }

    public Account unfreezeAccount(UUID accountId, Jwt jwt) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundWithUserIdException(accountId.toString()));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new AccountAlreadyActiveException(accountId.toString());
        }

        account.setStatus(AccountStatus.ACTIVE);
        Account savedAccount = accountRepository.save(account);
        evictProfileCache(account.getUserId());

        AccountUnfreezeEvent event = AccountUnfreezeEvent.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .unfreezeTime(Instant.now())
                .userId(account.getUserId())
                .email(account.getEmail())
                .build();

        kafkaEventPublisher.publish(kafkaTopics.getTopicAccountStatusChanged(), accountId.toString(), event);
        return savedAccount;
    }

    public BalanceViewProjection getBalanceInternal(String accountNumber) {
        return accountRepository.findBalanceByAccountNumber(accountNumber).orElseThrow(() -> new AccountNotFoundWithUserIdException(accountNumber));
    }

    private String generateAccountNumber() {
        // "KZ" + 18 random digits = 20 chars (the column length). The unique constraint
        // on account_number is the final guard against the negligible collision chance.
        StringBuilder sb = new StringBuilder("KZ");
        for (int i = 0; i < 18; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public AccountResponseDto toDto(Account account) {
        return accountMapper.toDto(account);
    }

    public void internalTransfer(InternalTransferRequestDto transferRequestDto) {
        String from = transferRequestDto.fromAccountNumber();
        String to = transferRequestDto.toAccountNumber();
        BigDecimal amount = transferRequestDto.amount();

        if (from.equals(to)) {
            throw new SelfTransferException(from);
        }

        // Lock both rows in a fixed order (by account number). If A->B and B->A run
        // at the same time, both lock the lower number first, so they cannot deadlock.
        List<String> lockOrder = List.of(from, to).stream().sorted(Comparator.naturalOrder()).toList();
        Account first = accountRepository.findByAccountNumberForUpdate(lockOrder.get(0))
                .orElseThrow(() -> new AccountNotFoundWithUserIdException(lockOrder.get(0)));
        Account second = accountRepository.findByAccountNumberForUpdate(lockOrder.get(1))
                .orElseThrow(() -> new AccountNotFoundWithUserIdException(lockOrder.get(1)));

        Account sender = first.getAccountNumber().equals(from) ? first : second;
        Account receiver = sender == first ? second : first;

        if(sender.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(from);
        }

        if(receiver.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(to);
        }

        if(sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(sender.getId());
        }

        CurrencyType fromCurrency = sender.getCurrency();
        CurrencyType toCurrency = receiver.getCurrency();

        sender.setBalance(sender.getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP));
        BigDecimal amountToCredit = convertAmount(amount, fromCurrency.name(), toCurrency.name());
        receiver.setBalance(receiver.getBalance().add(amountToCredit).setScale(2, RoundingMode.HALF_UP));

        accountRepository.saveAll(List.of(sender, receiver));
        evictProfileCache(sender.getUserId());
        evictProfileCache(receiver.getUserId());
    }

    private void evictProfileCache(UUID userId) {
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(ACCOUNTS_CACHE);
        if (cache != null) {
            cache.evict(userId.toString());
        }
    }

    private BigDecimal convertAmount(BigDecimal amount, String fromCurrency, String toCurrency){
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        CurrencyApiResponse response = currencyClientApi.convert(apiKey, fromCurrency, toCurrency);
        BigDecimal rate = response.getData().get(toCurrency).getValue();
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new AccountNotFoundWithUserIdException(accountNumber));
    }

    public AccountResponseDto getByUserId(UUID userId) {
        return accountMapper.toDto(accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundWithUserIdException(userId.toString())));
    }
}
