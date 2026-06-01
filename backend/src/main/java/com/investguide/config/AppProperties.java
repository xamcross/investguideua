package com.investguide.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-level [CONFIG] values (SPECIFICATION §2, §8.2, §9.1, §10, §14).
 *
 * <p>Defaults live in {@code application.yml} (the single source of default values, matching the
 * spec {@code [CONFIG: key=default]} convention) and are overridable via environment without a
 * rebuild. The records here intentionally do NOT patch zero values to defaults — doing so would
 * make a legitimately-configured {@code 0} (e.g. {@code app.signup.free-tokens: 0}) un-settable.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @NotNull @Valid Signup signup,
        @NotNull @Valid Rate rate,
        @NotNull @Valid Search search,
        @NotNull @Valid Pricing pricing,
        @NotNull @Valid Verification verification,
        @NotNull @Valid Cors cors
) {

    /** §4.1: free tokens granted on first email verification. */
    public record Signup(@Min(0) int freeTokens) {
    }

    /** §4.1, BE-A3: email-verification token lifetime (ms). */
    public record Verification(@Min(60_000) long tokenTtlMs) {
    }

    /** §4.1(3), §8.2: rate limits (per-instance for MVP scale). */
    public record Rate(
            @Min(1) int signupPerHourPerIp,
            @Min(1) int refreshPerHourPerIp,
            @Min(1) int searchPerMinute
    ) {
    }

    /** §5.2, §8.2: search bounds. */
    public record Search(
            @Min(1) long maxAmount,
            @Min(1) int maxOptions
    ) {
    }

    /**
     * §9.1, §14: seed-time pricing validation parameters (used by the X7 pricing validator).
     *
     * <p>{@code gatewayPercentFee} is the payment gateway's per-transaction card-acquiring commission
     * (§14: monobank "Plata by mono" = 1.3%), expressed as a fraction (0.013). {@code uahPerUsd} converts the LLM
     * cost-per-search (configured in USD on {@code llm.costPerSearchUsd}) into the UAH minor
     * units the pack price is denominated in, so the §9.1 inequality compares like with like.
     * It is a periodically-reviewed estimate, not a live FX feed — the safety margin (≈70x)
     * makes the result insensitive to small rate moves.
     */
    public record Pricing(
            @Min(1) int safetyMultiple,
            @Min(1) int minPackUah,
            @Min(0) long gatewayFixedFeeMinorUnits,
            @DecimalMin("0.0") @DecimalMax("1.0") double gatewayPercentFee,
            @DecimalMin("0.0") double uahPerUsd
    ) {
    }

    /** §10: CORS allowed origin (single app origin). */
    public record Cors(@NotBlank String allowedOrigin) {
    }
}
