package com.workhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP request/response filter that ensures every request has a correlation ID
 * for end-to-end request tracing.
 *
 * If the X-Correlation-ID header is present, it's used; otherwise, a new UUID is generated.
 * The correlation ID is placed in the SLF4J MDC so it appears in all log lines
 * (configured via logging.pattern.console in application.yml).
 */
@Slf4j
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Extract or generate correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Place in MDC for all log lines in this request thread
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        try {
            // Echo the correlation ID in the response header
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC to prevent leaks in thread pools
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
