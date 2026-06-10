package com.pm.accountservice.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CurrencyApiResponse {
    private Map<String, CurrencyData> data;
}
