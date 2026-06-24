package com.pm.accountservice.configuration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

public class KeycloakJwtConverter implements Converter<Jwt, JwtAuthenticationToken> {
    @Override
    public JwtAuthenticationToken convert(Jwt source) {
        Map<String, Object> realms = source.getClaim("realm_access");

        if (realms == null || realms.get("roles") == null) {
            return new JwtAuthenticationToken(source, List.of());
        }

        List<String> roles = (List<String>) realms.get("roles");

        return new JwtAuthenticationToken(
                source,
                roles.stream()
                        .map(role -> "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
}
