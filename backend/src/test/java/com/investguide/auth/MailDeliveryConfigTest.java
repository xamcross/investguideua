package com.investguide.auth;

import com.investguide.config.AppProperties;
import com.investguide.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for the {@link MailDeliveryConfig} bean selection (feature 003). Exercises the factory
 * method directly (no Spring context / no Mongo / no secrets needed - consistent with the existing
 * unit-test style) to prove which {@link VerificationNotifier} is wired in each mode:
 *
 * <ul>
 *   <li>US1 / T009: SMTP config -> {@link SmtpVerificationNotifier}</li>
 *   <li>US2 / T015: no config  -> {@link LoggingVerificationNotifier} (fallback), no crash</li>
 *   <li>US2 / T016: incomplete -> {@link LoggingVerificationNotifier} (fallback), no crash (FR-004)</li>
 * </ul>
 */
class MailDeliveryConfigTest {

    private final MailDeliveryConfig config = new MailDeliveryConfig();
    private final ThreadPoolTaskExecutor executor = config.mailExecutor();

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

    private VerificationNotifier wire(MailProperties mail) {
        return config.verificationNotifier(mail, appProps(), new VerificationEmailContent(appProps()), executor);
    }

    @Test
    void smtpConfig_wiresSmtpNotifier() {
        MailProperties mail = new MailProperties(true, "smtp.example.com", "587", "u", "p",
                "no-reply@investguide.ua", true, 7000, 7000, false);

        assertThat(wire(mail)).isInstanceOf(SmtpVerificationNotifier.class);
    }

    @Test
    void disabled_wiresLoggingFallback_andDoesNotCrash() {
        // Mirrors "no MAIL_* set": enabled=false, blank host/from (as ${MAIL_HOST:} would bind).
        MailProperties mail = new MailProperties(false, "", "587", "", "", "",
                true, 7000, 7000, false);

        assertThatCode(() -> assertThat(wire(mail)).isInstanceOf(LoggingVerificationNotifier.class))
                .doesNotThrowAnyException();
    }

    @Test
    void incompleteConfig_enabledButBlankHost_wiresLoggingFallback_andDoesNotCrash() {
        MailProperties mail = new MailProperties(true, "", "587", "", "", "no-reply@investguide.ua",
                true, 7000, 7000, false);

        assertThatCode(() -> assertThat(wire(mail)).isInstanceOf(LoggingVerificationNotifier.class))
                .doesNotThrowAnyException();
    }

    @Test
    void disabled_logsDescriptiveStartupMessage() {
        var logs = LogCapture.attach(MailDeliveryConfig.class);
        try {
            wire(new MailProperties(false, "", "587", "", "", "", true, 7000, 7000, false));
            assertThat(logs.joined()).contains("mail_delivery_disabled");
            assertThat(logs.joined()).contains("MAIL_ENABLED=true");   // tells operator how to enable
        } finally {
            logs.detach();
        }
    }

    @Test
    void incomplete_logsDescriptiveWarning_namingMissingKeys() {
        var logs = LogCapture.attach(MailDeliveryConfig.class);
        try {
            wire(new MailProperties(true, "", "587", "", "", "", true, 7000, 7000, false));
            // FR-005 / US2 AS-3: WARN naming exactly which required keys are blank.
            assertThat(logs.joined()).contains("mail_config_incomplete missing=host,from");
        } finally {
            logs.detach();
        }
    }

    @Test
    void smtp_logsEnabledBannerWithHostPortFrom_butNeverCredentials() {
        var logs = LogCapture.attach(MailDeliveryConfig.class);
        try {
            wire(new MailProperties(true, "smtp.example.com", "587", "smtp-user", "smtp-pass",
                    "no-reply@investguide.ua", true, 7000, 7000, false));
            assertThat(logs.joined()).contains("mail_delivery_enabled host=smtp.example.com port=587");
            assertThat(logs.joined()).doesNotContain("smtp-user");
            assertThat(logs.joined()).doesNotContain("smtp-pass");
        } finally {
            logs.detach();
        }
    }

    @Test
    void blankPort_isNormalised_andDoesNotCrashStartup() {
        // The FR-004 must-fix: a blank MAIL_PORT must not crash binding; normalised to 587.
        MailProperties mail = new MailProperties(true, "smtp.example.com", "", "u", "p",
                "no-reply@investguide.ua", true, 7000, 7000, false);

        assertThat(mail.portAsInt()).isEqualTo(587);
        assertThatCode(() -> wire(mail)).doesNotThrowAnyException();
    }
}
