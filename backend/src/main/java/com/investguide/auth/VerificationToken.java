package com.investguide.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Single-use, expiring email-verification token (ticket BE-A3).
 *
 * <p>Only the SHA-256 {@code tokenHash} is stored — never the raw token, which is delivered to the
 * user once (via {@link VerificationNotifier}). {@code expiresAt} carries a TTL index so Mongo
 * reaps expired tokens automatically. {@code used} marks single-use consumption defensively (the
 * actual one-time free-token grant is guarded atomically on the {@code User} document, so even a
 * race here cannot double-grant).
 */
@Document(collection = "verificationTokens")
public class VerificationToken {

    @Id
    private String id;

    @Indexed
    @Field("userId")
    private String userId;

    @Indexed(unique = true)
    @Field("tokenHash")
    private String tokenHash;

    /** TTL index: document is removed once {@code expiresAt} passes. */
    @Indexed(expireAfter = "0s")
    @Field("expiresAt")
    private Instant expiresAt;

    @Field("used")
    private boolean used = false;

    @Field("createdAt")
    private Instant createdAt;

    public VerificationToken() {
    }

    public static VerificationToken create(String userId, String tokenHash, Instant expiresAt) {
        VerificationToken t = new VerificationToken();
        t.userId = userId;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        t.used = false;
        t.createdAt = Instant.now();
        return t;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
