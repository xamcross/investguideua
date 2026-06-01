package com.investguide.common.web;

import com.investguide.common.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One structured access-log line per request (SPECIFICATION §8.2, §11; ticket X6 DoD #1).
 *
 * <p>Ordered just after {@link com.investguide.common.error.RequestIdFilter} (which is
 * {@code HIGHEST_PRECEDENCE}) and therefore <em>outside</em> the Spring Security chain, so it observes
 * the final response status and the full end-to-end duration — including auth rejections (401/403) and
 * rate-limit trips (429). The {@code requestId} is still in the MDC here (the request-id filter is the
 * outer wrapper); the authenticated {@code userId} is read from the request attribute set by
 * {@link JwtAuthenticationFilter} (its MDC value is cleared before this filter's post-step runs).
 *
 * <p>No PII: only method, path, status, duration, requestId and the opaque userId are logged — never
 * email, password, tokens, or request bodies (§8.2 / §10).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            Object userId = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
            boolean userIdInMdc = false;
            if (userId != null) {
                // Surface userId as a structured field (ECS) / log prefix (fallback pattern) for this
                // line, rather than inlining it into the message — avoids duplicating it in the output.
                MDC.put("userId", String.valueOf(userId));
                userIdInMdc = true;
            }
            try {
                log.info("http_request method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            } finally {
                if (userIdInMdc) {
                    MDC.remove("userId");
                }
            }
        }
    }
}
