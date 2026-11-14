package com.iplytics.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One structured log line per API request ({@code key=value}) with a request id that is echoed in the
 * {@code X-Request-Id} header and available to other log lines through the MDC key {@code requestId}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger("iplytics.http");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        String requestId = supplied != null && SAFE_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("http_request method={} path={} status={} durationMs={} requestId={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), (System.nanoTime() - started) / 1_000_000, requestId);
            MDC.remove("requestId");
        }
    }
}
