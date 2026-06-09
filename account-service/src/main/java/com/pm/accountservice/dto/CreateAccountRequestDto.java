package com.pm.accountservice.dto;

import com.pm.accountservice.entity.CurrencyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class CreateAccountRequestDto {

    @NotNull(message = "Please select currency type!")
    private CurrencyType currency;
}
