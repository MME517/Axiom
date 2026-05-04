package com.workhub.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        
        // Skip tenant context for public health endpoints
        String requestUri = request.getRequestURI();
        if (isPublicHealthEndpoint(requestUri)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof UsernamePasswordAuthenticationToken auth) {
                Object details = auth.getDetails();
                if (details instanceof Map<?, ?> detailsMap) {
                    String tenantId = (String) detailsMap.get("tenantId");
                    // This will throw TenantContextMissingException if null/blank
                    TenantContext.setTenantId(tenantId);
                }
            }
            chain.doFilter(request, response);

        } catch (com.workhub.exception.TenantContextMissingException ex) {
            // Fail fast — return 401 immediately
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(
                            Map.of("error", ex.getMessage(), "status", 401)
                    )
            );
        } finally {
            // Always clear — guarantees no tenant leakage
            TenantContext.clear();
        }
    }

    /**
     * Check if the request is to a public health endpoint that doesn't require tenant context
     */
    private boolean isPublicHealthEndpoint(String requestUri) {
        return requestUri.equals("/health/liveness") ||
               requestUri.equals("/health/readiness") ||
               requestUri.equals("/actuator/health") ||
               requestUri.equals("/actuator/health/liveness") ||
               requestUri.equals("/actuator/health/readiness") ||
               requestUri.equals("/auth/login");
    }
}
