package com.investguide.auth;

import com.investguide.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reads and writes the refresh-token HttpOnly cookie (ticket BE-A4 transport; FE-CORE2 §10).
 *
 * <p>The cookie is {@code HttpOnly} (not readable by JS, mitigating XSS token theft),
 * {@code Secure}/{@code SameSite} per config, and scoped to the auth path so it is only sent to
 * {@code /auth/refresh} (and login/logout). The raw refresh token never appears in a response body.
 */
@Component
public class RefreshCookieManager {

    private final SecurityProperties.RefreshCookie cfg;
    private final long refreshTtlMs;

    public RefreshCookieManager(SecurityProperties securityProperties) {
        this.cfg = securityProperties.refreshCookie();
        this.refreshTtlMs = securityProperties.refreshTtlMs();
    }

    /** Set the refresh cookie carrying {@code rawToken}. */
    public void write(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = baseBuilder(rawToken)
                .maxAge(Duration.ofMillis(refreshTtlMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Clear the refresh cookie (logout / invalid session). */
    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseBuilder("")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Extract the refresh token from the request cookie, or {@code null} if absent. */
    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var c : request.getCookies()) {
            if (cfg.name().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(cfg.name(), value)
                .httpOnly(true)
                .secure(cfg.secure())
                .sameSite(cfg.sameSite())
                .path(cfg.path());
    }
}
