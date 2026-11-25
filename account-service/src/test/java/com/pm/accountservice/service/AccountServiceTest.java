package com.pm.accountservice.service;

import com.pm.accountservice.client.CurrencyClientApi;
import com.pm.accountservice.dto.CreateAccountRequestDto;
import com.pm.accountservice.dto.CurrencyApiResponse;
import com.pm.accountservice.dto.CurrencyData;
import com.pm.accountservice.dto.InternalTransferRequestDto;
import com.pm.accountservice.dto.TopUpRequestDto;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.events.KafkaEventPublisher;
import com.pm.accountservice.events.KafkaTopics;
import com.pm.accountservice.exception.AccountAlreadyActiveException;
import com.pm.accountservice.exception.AccountFrozenException;
import com.pm.accountservice.exception.AccountNotFoundWithUserIdException;
import com.pm.accountservice.exception.InsufficientBalanceException;
import com.pm.accountservice.exception.SelfTransferException;
import com.pm.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CurrencyClientApi currencyClientApi;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private KafkaTopics kafkaTopics;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private AccountService accountService;

    private Account account;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .accountNumber("KZ123456789")
                .balance(BigDecimal.ZERO)
                .currency(CurrencyType.KZT)
                .status(AccountStatus.ACTIVE)
                .email("user@example.com")
                .build();

        // A real Jwt object instead of @AuthenticationPrincipal (which only works in controllers).
        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .claim("email", "user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private Account otherAccount(String number, BigDecimal balance) {
        return Account.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .accountNumber(number)
                .balance(balance)
                .currency(CurrencyType.KZT)
                .status(AccountStatus.ACTIVE)
                .email(number + "@example.com")
                .build();
    }

    @Test
    void createAccount_WhenRequestIsValid_ThenAccountIsSavedWithJwtData() {
        CreateAccountRequestDto request = new CreateAccountRequestDto();
        request.setCurrency(CurrencyType.KZT);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.createAccount(request, jwt);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getCurrency()).isEqualTo(CurrencyType.KZT);
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getAccountNumber()).startsWith("KZ").hasSize(20);
    }

    @Test
    void getMyProfile_WhenAccountExists_ThenReturnIt() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        Account result = accountService.getMyProfile(jwt);

        assertThat(result).isSameAs(account);
    }

    @Test
    void getMyProfile_WhenAccountDoesNotExist_ThenThrow() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundWithUserIdException.class, () -> accountService.getMyProfile(jwt));
    }

    @Test
    void topUpBalance_WhenSameCurrency_ThenBalanceIncreases() {
        account.setBalance(new BigDecimal("1000"));
        when(accountRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        TopUpRequestDto request = new TopUpRequestDto();
        request.setAmount(new BigDecimal("500"));
        request.setCurrencyType(CurrencyType.KZT);

        Account result = accountService.topUpBalance(request, jwt);

        assertThat(result.getBalance()).isEqualByComparingTo("1500");
        verifyNoInteractions(currencyClientApi);
    }

    @Test
    void topUpBalance_WhenDifferentCurrency_ThenConvertAndIncreaseBalance() {
        account.setBalance(new BigDecimal("1000"));
        // fake API response: 1 EUR = 450 KZT
        CurrencyApiResponse apiResponse = new CurrencyApiResponse(Map.of("KZT", new CurrencyData("KZT", new BigDecimal("450"))));

        when(accountRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        when(currencyClientApi.convert(any(), eq("EUR"), eq("KZT"))).thenReturn(apiResponse);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        TopUpRequestDto request = new TopUpRequestDto();
        request.setAmount(new BigDecimal("1"));
        request.setCurrencyType(CurrencyType.EUR);

        Account result = accountService.topUpBalance(request, jwt);

        assertThat(result.getBalance()).isEqualByComparingTo("1450");
    }

    @Test
    void topUpBalance_WhenAccountFrozen_ThenThrow() {
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(account));

        TopUpRequestDto request = new TopUpRequestDto();
        request.setAmount(new BigDecimal("500"));
        request.setCurrencyType(CurrencyType.USD);

        assertThrows(AccountFrozenException.class, () -> accountService.topUpBalance(request, jwt));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void freezeAccount_ThenStatusFrozenAndOwnersCacheEvicted() {
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("accounts")).thenReturn(cache);
        when(kafkaTopics.getTopicAccountStatusChanged()).thenReturn("account.status.changed");

        Account result = accountService.freezeAccount(account.getId(), jwt);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.FROZEN);
        verify(cache).evict(USER_ID.toString());
        verify(kafkaEventPublisher).publish(eq("account.status.changed"), anyString(), any());
    }

    @Test
    void unfreezeAccount_WhenFrozen_ThenStatusActive() {
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("accounts")).thenReturn(cache);
        when(kafkaTopics.getTopicAccountStatusChanged()).thenReturn("account.status.changed");

        Account result = accountService.unfreezeAccount(account.getId(), jwt);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(cache).evict(USER_ID.toString());
    }

    @Test
    void unfreezeAccount_WhenAlreadyActive_ThenThrowAlreadyActive() {
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThrows(AccountAlreadyActiveException.class, () -> accountService.unfreezeAccount(account.getId(), jwt));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void internalTransfer_WhenValid_ThenMovesMoneyAndLocksInSortedOrder() {
        Account sender = otherAccount("KZ900", new BigDecimal("100"));
        Account receiver = otherAccount("KZ100", new BigDecimal("0"));
        when(accountRepository.findByAccountNumberForUpdate("KZ100")).thenReturn(Optional.of(receiver));
        when(accountRepository.findByAccountNumberForUpdate("KZ900")).thenReturn(Optional.of(sender));

        accountService.internalTransfer(new InternalTransferRequestDto("KZ900", "KZ100", new BigDecimal("40")));

        assertThat(sender.getBalance()).isEqualByComparingTo("60");
        assertThat(receiver.getBalance()).isEqualByComparingTo("40");

        // lower account number is locked first, regardless of transfer direction
        InOrder inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByAccountNumberForUpdate("KZ100");
        inOrder.verify(accountRepository).findByAccountNumberForUpdate("KZ900");
    }

    @Test
    void internalTransfer_WhenInsufficientBalance_ThenThrowAndNothingSaved() {
        Account sender = otherAccount("KZ1", new BigDecimal("10"));
        Account receiver = otherAccount("KZ2", new BigDecimal("0"));
        when(accountRepository.findByAccountNumberForUpdate("KZ1")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumberForUpdate("KZ2")).thenReturn(Optional.of(receiver));

        assertThrows(InsufficientBalanceException.class, () ->
                accountService.internalTransfer(new InternalTransferRequestDto("KZ1", "KZ2", new BigDecimal("50"))));
        verify(accountRepository, never()).saveAll(any());
    }

    @Test
    void internalTransfer_WhenReceiverMissing_ThenErrorNamesReceiver() {
        Account sender = otherAccount("KZ1", new BigDecimal("10"));
        when(accountRepository.findByAccountNumberForUpdate("KZ1")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumberForUpdate("KZ2")).thenReturn(Optional.empty());

        AccountNotFoundWithUserIdException ex = assertThrows(AccountNotFoundWithUserIdException.class, () ->
                accountService.internalTransfer(new InternalTransferRequestDto("KZ1", "KZ2", new BigDecimal("5"))));
        assertThat(ex.getMessage()).contains("KZ2");
    }

    @Test
    void internalTransfer_WhenSameAccount_ThenThrow() {
        assertThrows(SelfTransferException.class, () ->
                accountService.internalTransfer(new InternalTransferRequestDto("KZ1", "KZ1", BigDecimal.ONE)));
    }
}
