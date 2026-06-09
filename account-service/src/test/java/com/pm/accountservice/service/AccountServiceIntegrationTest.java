package com.pm.accountservice.service;

import com.pm.accountservice.dto.CreateAccountRequestDto;
import com.pm.accountservice.entity.Account;
import com.pm.accountservice.entity.AccountStatus;
import com.pm.accountservice.entity.CurrencyType;
import com.pm.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest
@ActiveProfiles("test")
public class AccountServiceIntegrationTest {

    @Autowired
    private AccountService service;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp(){
        accountRepository.deleteAll();
    }

    @Test
    void createAccount_WhenRequestIsValid_ThenSaveAccountInDb(){
        CreateAccountRequestDto request = new CreateAccountRequestDto();
        request.setCurrency(CurrencyType.KZT);

        Account createdAccount = service.createAccount(request);

        assertThat(createdAccount).isNotNull();
        assertThat(createdAccount.getId()).isNotNull();
        assertThat(createdAccount.getCurrency()).isEqualTo(CurrencyType.KZT);
        assertThat(createdAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(createdAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        assertThat(accountRepository.findById(createdAccount.getId())).isPresent();
    }
}
