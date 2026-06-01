package com.investguide.auth;

/**
 * Delivers the email-verification link to a newly registered user (ticket BE-A3).
 *
 * <p>Abstracted so the delivery channel can change without touching auth business logic. The MVP
 * ships {@link LoggingVerificationNotifier} (logs the link — no SMTP secret required); a real
 * SMTP/provider implementation drops in later behind the same interface and a config flag.
 */
public interface VerificationNotifier {

    /**
     * Send the verification link for {@code email}. The {@code rawToken} is the one-time secret
     * (never persisted in raw form); implementations embed it in the verification URL/code.
     */
    void sendVerification(String email, String rawToken);
}
