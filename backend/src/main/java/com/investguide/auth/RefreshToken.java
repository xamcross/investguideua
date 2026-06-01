package com.investguide.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Persisted refresh-token record (SPECIFICATION §6 {@code refreshTokens}; ticket BE-A4).
 *
 * <p>Only the SHA-256 {@code tokenHash} is stored — never the raw JWT. A TTL index on
 * {@code expiresAt} reaps expired records. {@code revoked} supports rotation/logout: on every
 * {@code /auth/refresh} the presented token's record is revoked and a fresh one issued, so a
 * replayed (already-rotated) refresh token is rejected.
 */
@Document(collection = "refreshTokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    @Field("userId")
    private String userId;

    @Indexed(unique = true)
    @Field("tokenHash")
    private String tokenHash;

    @Indexed(expireAfter = "0s")
    @Field("expiresAt")
    private Instant expiresAt;

    @Field("revoked")
    private boolean revoked = false;

    @Field("createdAt")
    private Instant createdAt;

    public RefreshToken() {
    }

    public static RefreshToken create(String userId, String tokenHash, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        t.userId = userId;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        t.revoked = false;
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

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
