package com.pm.accountservice.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CurrencyData {
    private String code;
    private BigDecimal value;
}
