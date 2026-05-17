package com.workhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitFilter.class);

    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> windowStart = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS = 100;
    private static final long WINDOW_MS = 60_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Read tenantId directly from security context (same way TenantFilter does)
        String tenantId = extractTenantId();

        if (tenantId == null || tenantId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();

        windowStart.putIfAbsent(tenantId, now);
        if (now - windowStart.get(tenantId) > WINDOW_MS) {
            windowStart.put(tenantId, now);
            requestCounts.put(tenantId, new AtomicInteger(0));
        }

        requestCounts.putIfAbsent(tenantId, new AtomicInteger(0));
        int count = requestCounts.get(tenantId).incrementAndGet();

        if (count > MAX_REQUESTS) {
            log.warn("[RATE-LIMIT] Tenant {} exceeded rate limit ({} req/min)", tenantId, count);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests\",\"status\":429}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractTenantId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof UsernamePasswordAuthenticationToken auth) {
                Object details = auth.getDetails();
                if (details instanceof Map<?, ?> detailsMap) {
                    return (String) detailsMap.get("tenantId");
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return null;
    }
}