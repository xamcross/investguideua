package com.investguide.auth;

import com.investguide.common.error.ApiException;
import com.investguide.common.security.JwtService;
import com.investguide.common.security.TokenHashing;
import com.investguide.config.SecurityProperties;
import io.jsonwebtoken.JwtException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Issues, validates and rotates refresh tokens (ticket BE-A4).
 *
 * <p>Each refresh token is a signed JWT; only its SHA-256 hash is persisted in
 * {@code refreshTokens}. Rotation revokes the presented token via an <strong>atomic conditional
 * update</strong> ({@code {tokenHash, revoked:false} -> revoked:true}) so two concurrent refreshes
 * with the same token can never both succeed — exactly one wins, the other is rejected as a replay.
 */
@Service
public class RefreshTokenService {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final MongoTemplate mongoTemplate;

    public RefreshTokenService(JwtService jwtService,
                               SecurityProperties securityProperties,
                               MongoTemplate mongoTemplate) {
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
        this.mongoTemplate = mongoTemplate;
    }

    /** Issue a new refresh token for {@code userId}, persisting its hash. Returns the raw token. */
    public String issue(String userId) {
        String raw = jwtService.generateRefreshToken(userId);
        Instant expiresAt = Instant.now().plusMillis(securityProperties.refreshTtlMs());
        mongoTemplate.insert(RefreshToken.create(userId, TokenHashing.sha256Hex(raw), expiresAt));
        return raw;
    }

    /**
     * Validate the presented refresh token and atomically revoke it (rotation step). Returns the
     * owning {@code userId} on success.
     *
     * @throws ApiException {@code UNAUTHORIZED} if the token is invalid, unknown, expired, or has
     *                      already been rotated/revoked (replay).
     */
    public String validateAndRevoke(String rawRefreshToken) {
        String userId;
        try {
            userId = jwtService.parseRefreshToken(rawRefreshToken).getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            // Signature/shape/expiry failure — do not echo the token or the reason.
            throw ApiException.unauthorized("Invalid or expired session. Please sign in again.");
        }

        String hash = TokenHashing.sha256Hex(rawRefreshToken);
        // Atomic: only an existing, not-yet-revoked, not-yet-expired record matches. The winner
        // of a concurrent race flips revoked=true; everyone else matches 0 docs -> rejected.
        Query query = Query.query(Criteria.where("tokenHash").is(hash)
                .and("revoked").is(false)
                .and("expiresAt").gt(Instant.now()));
        Update update = new Update().set("revoked", true);
        var result = mongoTemplate.updateFirst(query, update, RefreshToken.class);
        if (result.getModifiedCount() != 1) {
            throw ApiException.unauthorized("Invalid or expired session. Please sign in again.");
        }
        return userId;
    }
}
