package com.pm.accountservice.service;

import com.pm.accountservice.client.CurrencyClientApi;
import com.pm.accountservice.dto.*;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.exception.AccountFrozenException;
import com.pm.accountservice.exception.AccountNotFoundWithUserIdException;
import com.pm.accountservice.mapper.AccountMapper;
import com.pm.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final CurrencyClientApi currencyClientApi;

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

    public BigDecimal getConvertedBalance(ViewOrChangeCurrencyDto dto) {
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        BalanceViewProjection balance = accountRepository.findBalanceByUserId(dummyId).orElseThrow(() -> new AccountNotFoundWithUserIdException(dummyId.toString()));
        String targetCurrency = dto.getCurrency().name();

        CurrencyApiResponse currencyApiResponse = currencyClientApi.convert(
            apiKey,
            balance.getBalance(),
            balance.getCurrency().name(),
            targetCurrency
        );

        BigDecimal rate = currencyApiResponse.getData().get(targetCurrency).getValue();

        return balance.getBalance().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public Account topUpBalance(TopUpRequestDto topUpRequestDto) {
        UUID dummyId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Account account = accountRepository.findByUserId(dummyId).orElseThrow(() -> new AccountNotFoundWithUserIdException(dummyId.toString()));
        String currencyType = topUpRequestDto.getCurrencyType().name();
        BigDecimal amount = topUpRequestDto.getAmount();

        String accountCurrency = account.getCurrency().name();

        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(dummyId.toString());
        }

        if (accountCurrency.equals(currencyType)) {
            account.setBalance(account.getBalance().add(amount));
        } else {
            CurrencyApiResponse currencyApiResponse = currencyClientApi.convert(
                    apiKey,
                    amount,
                    currencyType,
                    accountCurrency
            );

            BigDecimal targetToTopUp = currencyApiResponse.getData().get(accountCurrency).getValue();
            account.setBalance(account.getBalance().add(targetToTopUp));
        }

        return accountRepository.save(account);
    }

    private String generateAccountNumber() {
        return "KZ" + System.currentTimeMillis();
    }

    public AccountResponseDto toDto(Account account) {
        return accountMapper.toDto(account);
    }
}
