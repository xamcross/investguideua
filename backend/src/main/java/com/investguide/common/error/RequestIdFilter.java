package com.investguide.common.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns/propagates a {@code requestId} per request (SPECIFICATION §5.3, §11).
 *
 * <p>The id is placed in the SLF4J MDC (for structured logs — X6), exposed as a request
 * attribute (read by the exception handler and the security entry points), and echoed back in
 * the {@code X-Request-Id} response header. An inbound {@code X-Request-Id} is honoured if the
 * caller (e.g. a trusted proxy) supplies one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = StringUtils.hasText(incoming) ? sanitize(incoming) : UUID.randomUUID().toString();
        try {
            MDC.put(MDC_KEY, requestId);
            request.setAttribute(ATTRIBUTE, requestId);
            response.setHeader(HEADER, requestId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Defend against log/header injection from an untrusted inbound value. */
    private static String sanitize(String raw) {
        String trimmed = raw.trim();
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64);
        }
        return trimmed.replaceAll("[^A-Za-z0-9._-]", "");
    }

    /** Convenience accessor used by error responders. */
    public static String current() {
        String fromMdc = MDC.get(MDC_KEY);
        return fromMdc != null ? fromMdc : "unknown";
    }
}
