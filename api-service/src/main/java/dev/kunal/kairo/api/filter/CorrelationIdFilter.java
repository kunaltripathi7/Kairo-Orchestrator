package dev.kunal.kairo.api.filter;

/* 
 * ---------------------------------------------------------
 * LEARNING REFERENCE: Manual Correlation ID Filter
 * ---------------------------------------------------------
 * This class was previously used to manually generate and 
 * inject a correlation ID into the MDC for log tracing.
 * 
 * It has been disabled because we now use Micrometer Tracing 
 * (OpenTelemetry) which automatically intercepts HTTP requests 
 * and injects traceId/spanId into MDC and Kafka headers.
 * 
 * Keeping this here for reference on how manual MDC tracing 
 * works in a standard Spring Boot application.
 * 
import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-Id";
    private static final String CORRELATION_ID_LOG_VAR_NAME = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER_NAME);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CORRELATION_ID_LOG_VAR_NAME, correlationId);
        response.setHeader(CORRELATION_ID_HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_LOG_VAR_NAME); // app code has run at this point
        }
    }
}
*/
