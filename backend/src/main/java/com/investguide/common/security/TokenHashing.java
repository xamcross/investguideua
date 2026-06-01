package com.investguide.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Deterministic hashing + secure random generation for opaque, single-use secrets
 * (email-verification tokens, refresh tokens). Used by BE-A3 and BE-A4.
 *
 * <p>These secrets are long, high-entropy random strings, so a fast SHA-256 hash is the right
 * tool: it is deterministic (enabling O(1) lookup by hash) yet preimage-resistant, so a database
 * leak does not expose usable tokens. <strong>Passwords</strong> (low entropy, attacker-guessable)
 * must NOT use this — they go through BCrypt ({@code PasswordEncoder}). Raw tokens are never stored
 * or logged; only their hashes are persisted.
 */
public final class TokenHashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHashing() {
    }

    /** Generate a URL-safe, 256-bit random token (the raw value shown once to the user). */
    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest of a raw token, for storage/lookup. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; absence is unrecoverable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
