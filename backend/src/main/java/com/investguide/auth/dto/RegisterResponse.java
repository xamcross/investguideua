package com.investguide.auth.dto;

/**
 * {@code POST /auth/register} response (ticket BE-A2).
 *
 * <p>Registration deliberately does NOT establish a session or grant tokens — free tokens arrive
 * only on email verification (§4.1, BE-A3). The response confirms the account was created and
 * that the user must verify their email. It never includes {@code passwordHash} or any token.
 */
public record RegisterResponse(
        String userId,
        String email,
        boolean emailVerified,
        String message
) {
    public static RegisterResponse created(String userId, String email) {
        return new RegisterResponse(
                userId,
                email,
                false,
                "Account created. Check your email to verify and receive 5 free tokens.");
    }
}
