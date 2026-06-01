package com.investguide.auth.dto;

import com.investguide.user.dto.UserProfileResponse;

/**
 * {@code POST /auth/login} and {@code POST /auth/refresh} JSON response (ticket BE-A4).
 *
 * <p>Only the short-lived <strong>access</strong> token is in the body (held in memory by the SPA,
 * FE-CORE2). The refresh token is NEVER in the body — it is delivered via an HttpOnly, Secure,
 * SameSite cookie set on the same response, so JS/XSS cannot read it (§10, AC #10).
 */
public record AuthResponse(
        String accessToken,
        long expiresInMs,
        UserProfileResponse user
) {
}
