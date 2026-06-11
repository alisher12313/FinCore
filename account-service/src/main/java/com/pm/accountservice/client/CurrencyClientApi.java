package com.pm.accountservice.client;

import com.pm.accountservice.dto.CurrencyApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "currency-api", url = "${currency.url}")
public interface CurrencyClientApi {

    @GetMapping("/convert")
    public CurrencyApiResponse convert(
            @RequestHeader("apiKey") String apiKey,
            @RequestParam("value") BigDecimal value,
            @RequestParam("base_currency") String baseCurrency,
            @RequestParam("currencies") String targetCurrency
            );
}
