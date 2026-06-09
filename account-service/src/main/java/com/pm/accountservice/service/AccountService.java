package com.pm.accountservice.service;

import com.pm.accountservice.dto.AccountResponseDto;
import com.pm.accountservice.dto.CreateAccountRequestDto;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.mapper.AccountMapper;
import com.pm.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

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

    private String generateAccountNumber() {
        return "KZ" + System.currentTimeMillis();
    }

    public AccountResponseDto toDto(Account account) {
        return accountMapper.toDto(account);
    }
}
