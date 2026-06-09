package com.pm.accountservice.service;

import com.pm.accountservice.dto.CreateAccountRequestDto;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest{

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account account;
    private CreateAccountRequestDto createAccountRequestDto;

    @BeforeEach
    void setUp(){
        createAccountRequestDto = new CreateAccountRequestDto();
        createAccountRequestDto.setCurrency(CurrencyType.KZT);

        account = Account.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .accountNumber("KZ123456789")
                .balance(BigDecimal.ZERO)
                .currency(CurrencyType.KZT)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void testCreateAccount_WhenRequestIsValid_ThenCreatedAccount(){
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        Account result = accountService.createAccount(createAccountRequestDto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(account.getId());
        assertThat(result.getCurrency()).isEqualTo(CurrencyType.KZT);
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        Mockito.verify(accountRepository, times(1)).save(any(Account.class));
    }
}

