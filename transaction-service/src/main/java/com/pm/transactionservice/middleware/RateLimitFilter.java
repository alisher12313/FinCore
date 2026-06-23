package com.pm.transactionservice.middleware;

import com.pm.transactionservice.repository.nosql.AuditLogRepository;
import com.pm.transactionservice.service.RateLimiterForTransactionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterForTransactionService service;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if (request.getMethod().equals("POST") && request.getRequestURI().equals("/transfer")) {
            String client = Optional.ofNullable(request.getRemoteAddr()).orElse("unknown");

            boolean allowed = service.allow(client, 5, Duration.ofMinutes(1));

            if (!allowed) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Try again in a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
