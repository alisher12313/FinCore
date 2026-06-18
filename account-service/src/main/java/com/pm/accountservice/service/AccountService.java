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
import com.pm.accountservice.mapper.AccountMapper;
import com.pm.accountservice.repository.AccountRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    @Value("${currency.api}")
    private String apiKey;

    public Account createAccount(CreateAccountRequestDto accountRequest) {
        log.info("Creating account {}", accountRequest.toString());
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String accountNumber = generateAccountNumber();
        BigDecimal balance = new BigDecimal(0);
        CurrencyType currencyType = accountRequest.getCurrency();
        AccountStatus status = AccountStatus.ACTIVE;

        Account account = Account.builder()
                .userId(dummyId)
                .accountNumber(accountNumber)
                .balance(balance)
                .currency(currencyType)
                .status(status)
                .build();

        return accountRepository.save(account);
    }

    //todo: Inject Jwt token later using @AuthenticationPrincipal
    public Account getMyProfile(){
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        return accountRepository.findByUserId(dummyId).orElseThrow(() -> new AccountNotFoundWithUserIdException(dummyId.toString()));
    }

    //todo: Inject Jwt token later using @AuthenticationPrincipal
    public BigDecimal getConvertedBalance(@NotNull(message = "Currency must be selected!") CurrencyType type) {
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");

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

    //todo: Inject Jwt token later using @AuthenticationPrincipal
    public Account topUpBalance(TopUpRequestDto topUpRequestDto) {
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Account account = accountRepository.findByUserId(dummyId).orElseThrow(() -> new AccountNotFoundWithUserIdException(dummyId.toString()));
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

    //todo: Inject Jwt token later using @AuthenticationPrincipal
    public Account freezeAccount(UUID accountId) {
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Account account = accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundWithUserIdException(accountId.toString()));

        if(account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(accountId.toString());
        }

        account.setStatus(AccountStatus.FROZEN);
        Account savedAccount = accountRepository.save(account);

        AccountFreezeEvent event = AccountFreezeEvent.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .freezeTime(Instant.now())
                .userId(dummyId)
                .build();

        kafkaEventPublisher.publish(kafkaTopics.getTopicAccountStatusChanged(), accountId.toString(), event);
        return savedAccount;
    }

    public Account unfreezeAccount(UUID accountId) {

        UUID dummyId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundWithUserIdException(accountId.toString()));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new AccountAlreadyActiveException(accountId.toString());
        }

        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);

        AccountUnfreezeEvent event = AccountUnfreezeEvent.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .userId(dummyId)
                .unfreezeTime(Instant.now())
                .build();

        kafkaEventPublisher.publish(
                kafkaTopics.getTopicAccountStatusChanged(),
                accountId.toString(),
                event
        );

        return savedAccount;
    }

    public BalanceViewProjection getBalanceInternal(String accountNumber) {
        return accountRepository.findBalanceByAccountNumber(accountNumber).orElseThrow(() -> new AccountNotFoundWithUserIdException(accountNumber));
    }

    private String generateAccountNumber() {
        return "KZ" + System.currentTimeMillis();
    }

    public AccountResponseDto toDto(Account account) {
        return accountMapper.toDto(account);
    }

    public void internalTransfer(InternalTransferRequestDto transferRequestDto) {
        String from = transferRequestDto.fromAccountNumber();
        String to = transferRequestDto.toAccountNumber();
        BigDecimal amount = transferRequestDto.amount();

        Account sender = accountRepository.findByAccountNumber(from).orElseThrow(() -> new AccountNotFoundWithUserIdException(from));
        Account receiver = accountRepository.findByAccountNumber(to).orElseThrow(() -> new AccountNotFoundWithUserIdException(from));

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
    }

    private BigDecimal convertAmount(BigDecimal amount, String fromCurrency, String toCurrency){
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        CurrencyApiResponse response = currencyClientApi.convert(apiKey, fromCurrency, toCurrency);
        BigDecimal rate = response.getData().get(toCurrency).getValue();
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
