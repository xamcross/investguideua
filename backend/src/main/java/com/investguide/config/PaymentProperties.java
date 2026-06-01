package com.investguide.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Payment / monobank "Plata by mono" [CONFIG] (SPECIFICATION §9). The merchant {@code token} (X-Token)
 * is a SECRET supplied via environment.
 *
 * <p><b>Intentionally NOT required at startup.</b> The app must boot without a real merchant token —
 * a working, deployed webapp has to be presented to the bank before they issue the production token.
 * Until {@code MONO_TOKEN} is set, payment <em>creation</em> simply fails at runtime with a clear
 * gateway error (502); everything else (auth, search, history) works. The token is defaulted to an
 * empty string so a missing env var neither breaks placeholder resolution nor fails validation.
 */
@Validated
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @Valid Mono mono,
        @NotBlank String resultUrl,
        @NotBlank String callbackUrl
) {

    /**
     * monobank acquiring settings. {@code token} is the merchant X-Token secret (may be blank
     * pre-onboarding — see class javadoc). {@code apiBaseUrl} defaults to the production host and MUST
     * stay HTTPS (the public-key fetch trusts it). The pubkey cache + refresh-cooldown bound how often
     * a (possibly forged) callback can trigger an outbound key fetch (§9.3 anti-amplification).
     */
    @Validated
    public record Mono(
            String token,
            String apiBaseUrl,
            long invoiceValiditySeconds,
            long requestTimeoutMs,
            long pubkeyCacheTtlMs,
            long pubkeyMinRefreshIntervalMs
    ) {
        public Mono {
            if (token == null) {
                token = ""; // never null — a blank token yields a 401 from mono -> a clean 502, not an NPE
            }
            if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
                apiBaseUrl = "https://api.monobank.ua";
            }
            if (invoiceValiditySeconds <= 0) {
                invoiceValiditySeconds = 3600;          // 1h checkout window
            }
            if (requestTimeoutMs <= 0) {
                requestTimeoutMs = 7000;
            }
            if (pubkeyCacheTtlMs <= 0) {
                pubkeyCacheTtlMs = 3_600_000;           // re-fetch a fresh key at most hourly
            }
            if (pubkeyMinRefreshIntervalMs <= 0) {
                pubkeyMinRefreshIntervalMs = 60_000;    // refresh-on-failure no more than once/min
            }
        }
    }
}
