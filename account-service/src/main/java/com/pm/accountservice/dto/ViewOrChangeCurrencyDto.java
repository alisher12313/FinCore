package com.pm.accountservice.dto;

import com.pm.accountservice.entity.CurrencyType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ViewOrChangeCurrencyDto {
    @NotNull(message = "Please select currency type!")
    private CurrencyType currency;
}
