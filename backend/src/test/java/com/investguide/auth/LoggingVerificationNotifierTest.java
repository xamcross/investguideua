package com.investguide.auth;

import com.investguide.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LoggingVerificationNotifier} (feature 003, US2 / T017). Verifies the
 * SC-005 carve-out: by default the raw verification link/token is NOT logged; only the explicit
 * dev-only {@code mail.log-link=true} override logs it.
 */
class LoggingVerificationNotifierTest {

    private static AppProperties appProps() {
        return new AppProperties(
                "http://localhost:4200",
                new AppProperties.Signup(5),
                new AppProperties.Rate(10, 100, 5),
                new AppProperties.Search(100_000_000L, 5),
                new AppProperties.Pricing(10, 50, 0L, 0.0275, 44.5),
                new AppProperties.Verification(86_400_000L),
                new AppProperties.Cors("http://localhost:4200"));
    }

    @Test
    void default_doesNotLogRawLinkOrToken() {
        var logs = LogCapture.attach(LoggingVerificationNotifier.class);
        try {
            new LoggingVerificationNotifier(appProps(), false)
                    .sendVerification("user@example.com", "SECRET-TOKEN");

            assertThat(logs.joined()).contains("verification_email_disabled");
            assertThat(logs.joined()).contains("user@example.com");
            assertThat(logs.joined()).doesNotContain("SECRET-TOKEN");
            assertThat(logs.joined()).doesNotContain("/verify?token=");
        } finally {
            logs.detach();
        }
    }

    @Test
    void devOverride_logsLink_whenLogLinkTrue() {
        var logs = LogCapture.attach(LoggingVerificationNotifier.class);
        try {
            new LoggingVerificationNotifier(appProps(), true)
                    .sendVerification("user@example.com", "SECRET-TOKEN");

            assertThat(logs.joined()).contains("http://localhost:4200/verify?token=SECRET-TOKEN");
        } finally {
            logs.detach();
        }
    }
}
