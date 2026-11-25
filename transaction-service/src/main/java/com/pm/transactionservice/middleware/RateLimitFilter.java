package com.pm.transactionservice.middleware;

import com.pm.transactionservice.service.RateLimiterForTransactionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Limits POST /transfer to 5 requests per minute per user.
 *
 * Not a @Component on purpose: it is added to the Spring Security chain after the JWT
 * has been validated (see SecurityConfiguration), so the user id is available. Keying on
 * the client IP would put every user behind the gateway into one shared bucket.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiterForTransactionService service;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if ("POST".equals(request.getMethod()) && "/transfer".equals(request.getRequestURI())
                && auth != null && auth.isAuthenticated()) {
            // for JwtAuthenticationToken, getName() is the JWT "sub" claim (the user id)
            if (!service.allow(auth.getName(), LIMIT, WINDOW)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Try again in a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
