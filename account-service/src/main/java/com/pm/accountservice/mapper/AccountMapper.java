package com.pm.accountservice.mapper;

import com.pm.accountservice.dto.AccountResponseDto;
import com.pm.accountservice.entity.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponseDto toDto(Account account);

}
