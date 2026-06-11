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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest{

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CurrencyClientApi currencyClientApi;

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

    @Test
    void testGetMyProfile_WhenAccountExists_ThenReturnMyProfile(){
        when(accountRepository.findByUserId(any(UUID.class))).thenReturn(Optional.ofNullable(account));

        Account result = accountService.getMyProfile();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(account.getId());
        assertThat(result.getUserId()).isEqualTo(account.getUserId());

        Mockito.verify(accountRepository, times(1)).findByUserId(any(UUID.class));
    }

    @Test
    void testGetMyProfile_WhenAccountDoesNotExist_ThenThrowException(){
        when(accountRepository.findByUserId(any(UUID.class)))
                .thenReturn(Optional.empty())
                .thenThrow();

        assertThrows(AccountNotFoundWithUserIdException.class, () ->
                accountService.getMyProfile());
    }

    @Test
    void testTopUpBalance_WhenSameCurrency_ThenBalanceIncreases(){
        account.setBalance(new BigDecimal("1000"));
        account.setCurrency(CurrencyType.KZT);

        when(accountRepository.findByUserId(any(UUID.class)))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
                .thenReturn(account);


        TopUpRequestDto request = new TopUpRequestDto();
        request.setAmount(new BigDecimal("500"));
        request.setCurrencyType(CurrencyType.KZT);

        Account result = accountService.topUpBalance(request);

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1500"));
        Mockito.verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testTopUpBalance_WhenDifferentCurrency_ThenConvertAndIncreaseBalance() {
        account.setBalance(new BigDecimal("1000"));
        account.setCurrency(CurrencyType.KZT);

        // mock fake API response: 1 EUR = 450 KZT
        CurrencyData kztData = new CurrencyData("KZT", new BigDecimal("450"));
        CurrencyApiResponse apiResponse = new CurrencyApiResponse(Map.of("KZT", kztData));

        when(accountRepository.findByUserId(any(UUID.class)))
                .thenReturn(Optional.of(account));
        when(currencyClientApi.convert(any(), any(), any(), any()))
                .thenReturn(apiResponse);
        when(accountRepository.save(any(Account.class)))
                .thenReturn(account);

        TopUpRequestDto request = new TopUpRequestDto();
        request.setAmount(new BigDecimal("1"));
        request.setCurrencyType(CurrencyType.EUR);

        Account result = accountService.topUpBalance(request);

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1450"));
        Mockito.verify(currencyClientApi, times(1)).convert(any(), any(), any(), any());
        Mockito.verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testTopUpBalance_WhenAccountFrozen_ThenThrowException(){
        account.setStatus(AccountStatus.FROZEN);

        when(accountRepository.findByUserId(any(UUID.class))).thenReturn(Optional.of(account));

        TopUpRequestDto request = new TopUpRequestDto();
        request.setAmount(new BigDecimal("500"));
        request.setCurrencyType(CurrencyType.USD);

        assertThrows(AccountFrozenException.class, () ->
                accountService.topUpBalance(request));

        Mockito.verify(accountRepository, never()).save(any(Account.class));
    }
}

