package com.investguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Verification-email / SMTP [CONFIG] (feature 003). Delivery is an <b>optional</b> capability: the
 * app MUST build, start, and register users even when none of these are set (FR-004). Completeness
 * is therefore enforced at bean-selection time ({@link com.investguide.auth.MailDeliveryMode}), NOT
 * by {@code @NotBlank} validation here, so a missing/blank value never fails startup.
 *
 * <p>{@code username}/{@code password} are SECRETS supplied via environment ({@code MAIL_USERNAME}/
 * {@code MAIL_PASSWORD}); they are never defaulted in {@code application.yml} and never logged
 * (see {@link #toString()}).
 *
 * <p><b>Blank-safe port (FR-004).</b> {@code port} is bound as a {@code String}, not an {@code int}:
 * a blank {@code MAIL_PORT=''} would fail {@code int} binding <em>before</em> any normalization could
 * run and crash startup. The compact constructor normalizes a blank/garbage port to {@code 587}, and
 * {@link #portAsInt()} exposes the numeric value (only consulted in SMTP mode).
 *
 * <p>Intentionally NO Bean Validation constraints / {@code @Validated}: required-field completeness is
 * a runtime selection concern ({@link com.investguide.auth.MailDeliveryMode}), and every value is
 * defaulted in the compact constructor, so binding can never fail startup (FR-004).
 */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(
        boolean enabled,
        String host,
        String port,
        String username,
        String password,
        String from,
        boolean startTls,
        int connectTimeoutMs,
        int readTimeoutMs,
        boolean logLink
) {

    private static final int DEFAULT_PORT = 587;

    public MailProperties {
        port = normalisePort(port);
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 7000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 7000;
        }
    }

    /** Numeric SMTP port (already normalised; never throws). Consulted only in SMTP mode. */
    public int portAsInt() {
        return Integer.parseInt(port);
    }

    /**
     * Normalise to a valid numeric port string so a blank or non-numeric {@code MAIL_PORT} can never
     * crash startup (FR-004); falls back to {@code 587}.
     */
    private static String normalisePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return String.valueOf(DEFAULT_PORT);
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? String.valueOf(parsed) : String.valueOf(DEFAULT_PORT);
        } catch (NumberFormatException ex) {
            return String.valueOf(DEFAULT_PORT);
        }
    }

    /** Never expose the SMTP credentials (FR-003 / SC-005). */
    @Override
    public String toString() {
        return "MailProperties{enabled=" + enabled
                + ", host=" + host
                + ", port=" + port
                + ", from=" + from
                + ", startTls=" + startTls
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", logLink=" + logLink
                + ", username=***, password=***}";
    }
}
