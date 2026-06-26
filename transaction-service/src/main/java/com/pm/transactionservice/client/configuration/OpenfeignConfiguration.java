package com.pm.transactionservice.client.configuration;

import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

@Configuration
@RequiredArgsConstructor
public class OpenfeignConfiguration {

    private final OAuth2AuthorizedClientManager manager;

    @Bean
    public RequestInterceptor getRequestInterceptor() {
        return requestTemplate -> {
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                    .withClientRegistrationId("account-m2m")
                    .principal("transfer-client")
                    .build();

            OAuth2AuthorizedClient client = manager.authorize(request);

            requestTemplate.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
