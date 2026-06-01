package com.investguide.common.security;

import com.investguide.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * X4: JWT issuance/validation round-trip and type separation (access vs refresh).
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123456"; // >= 32 bytes
    private static final SecurityProperties.RefreshCookie COOKIE =
            new SecurityProperties.RefreshCookie("refresh_token", true, "None", "/api/v1/auth");

    private JwtService service(long accessTtl) {
        return new JwtService(new SecurityProperties(SECRET, "investguide", accessTtl, 1_000_000L, COOKIE));
    }

    @Test
    void accessTokenRoundTripsWithRoles() {
        JwtService svc = service(900_000L);
        String token = svc.generateAccessToken("user-1", List.of("USER"));
        Claims claims = svc.parseAccessToken(token);
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(svc.roles(claims)).containsExactly("USER");
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtService svc = service(900_000L);
        String refresh = svc.generateRefreshToken("user-1");
        assertThatThrownBy(() -> svc.parseAccessToken(refresh)).isInstanceOf(JwtException.class);
        assertThat(svc.parseRefreshToken(refresh).getSubject()).isEqualTo("user-1");
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService svc = service(900_000L);
        String token = svc.generateAccessToken("user-1", List.of("USER"));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "b" : "a");
        assertThatThrownBy(() -> svc.parseAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredAccessTokenIsRejected() throws InterruptedException {
        JwtService svc = service(1L); // expires almost immediately
        String token = svc.generateAccessToken("user-1", List.of("USER"));
        Thread.sleep(50);
        assertThatThrownBy(() -> svc.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRejectedAtConstruction() {
        SecurityProperties weak = new SecurityProperties("short", "investguide", 900_000L, 1_000_000L, COOKIE);
        assertThatThrownBy(() -> new JwtService(weak)).isInstanceOf(IllegalStateException.class);
    }
}
