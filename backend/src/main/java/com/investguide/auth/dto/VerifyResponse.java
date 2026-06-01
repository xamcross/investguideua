package com.investguide.auth.dto;

/**
 * {@code POST /auth/verify} response (ticket BE-A3). Reflects the verified state and the live
 * balance so the SPA (FE-AUTH3) can show {@code tokenBalance = 5} after a first verification.
 * {@code firstVerification} is {@code false} on idempotent replays (balance unchanged).
 */
public record VerifyResponse(
        boolean emailVerified,
        int tokenBalance,
        boolean firstVerification,
        String message
) {
}
