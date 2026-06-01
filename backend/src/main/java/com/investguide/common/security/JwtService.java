package com.investguide.common.security;

import com.investguide.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Issues and validates JWTs (SPECIFICATION §2, §4.1, §10).
 *
 * <p>Two token types are distinguished by a {@code typ} claim: short-lived {@code access}
 * (≤15 min) and longer-lived {@code refresh}. Signed with HMAC-SHA256 using the configured
 * secret (never logged, never client-exposed). The crypto here is shared by BE-A (auth) which
 * owns issuance flows and refresh-token persistence.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLES = "roles";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final SecurityProperties props;

    public JwtService(SecurityProperties props) {
        this.props = props;
        byte[] secret = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            // HS256 needs >= 256-bit key material; fail loudly rather than silently weakening.
            throw new IllegalStateException(
                    "security.jwt-secret must be at least 32 bytes for HS256 signing.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String generateAccessToken(String userId, List<String> roles) {
        return build(userId, TYPE_ACCESS, props.accessTtlMs(), Map.of(CLAIM_ROLES, roles));
    }

    public String generateRefreshToken(String userId) {
        return build(userId, TYPE_REFRESH, props.refreshTtlMs(), Map.of());
    }

    private String build(String subject, String type, long ttlMs, Map<String, Object> extra) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.jwtIssuer())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .claim(CLAIM_TYPE, type)
                .claims(extra)
                .signWith(key)
                .compact();
    }

    /** Parse+verify an access token. Throws {@link JwtException} on any failure. */
    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not an access token.");
        }
        return claims;
    }

    /** Parse+verify a refresh token. Throws {@link JwtException} on any failure. */
    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not a refresh token.");
        }
        return claims;
    }

    @SuppressWarnings("unchecked")
    public List<String> roles(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        return roles instanceof List ? (List<String>) roles : List.of();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.jwtIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
