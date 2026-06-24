package com.pm.accountservice.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

import javax.swing.*;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);

        http.oauth2ResourceServer(auth -> {
            auth.jwt(jwt -> {
                jwt.jwtAuthenticationConverter(new KeycloakJwtConverter());
            });
        });

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/account/internal/**").permitAll();
            auth.requestMatchers("/account/*/freeze", "/account/*/unfreeze").hasRole("admin");
            auth.requestMatchers("/account/**").hasRole("user");
            auth.anyRequest().authenticated();
        });

        http.sessionManagement(session -> {
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        });

        http.oauth2Login(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("http://localhost:9090/realms/fincore");
    }
}
