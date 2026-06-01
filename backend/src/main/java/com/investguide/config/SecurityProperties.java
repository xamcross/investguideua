package com.investguide.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT / security [CONFIG] (SPECIFICATION §2, §4.1, §10).
 *
 * <p>{@code jwtSecret} is a SECRET — environment only, never committed/logged. Access TTL is
 * capped at 15 min per §10 (enforced by {@link Range}). Startup fails fast if the signing secret
 * is missing or out-of-range TTLs are configured. Non-secret defaults live in
 * {@code application.yml}; values are not patched here.
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @NotBlank String jwtSecret,
        @NotBlank String jwtIssuer,
        @Range(min = 60_000, max = 900_000) long accessTtlMs,
        @Min(60_000) long refreshTtlMs,
        @NotNull @Valid RefreshCookie refreshCookie
) {

    /**
     * Attributes of the HttpOnly cookie that carries the refresh token (BE-A4 / FE-CORE2 §10).
     * The refresh token is never readable by JS; {@code secure}/{@code sameSite} are configurable
     * so plain-HTTP local dev can relax them while production stays Secure + SameSite=None.
     */
    public record RefreshCookie(
            @NotBlank String name,
            boolean secure,
            @NotBlank String sameSite,
            @NotBlank String path
    ) {
    }
}
