package com.investguide.auth;

import com.investguide.config.MailProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure {@link MailDeliveryMode#decide} selection (feature 003, T006/T007).
 * Verifies the disabled / incomplete / smtp decision and, critically, that an empty string (what
 * {@code ${MAIL_HOST:}} binds) is treated as absent - not only {@code null}.
 */
class MailDeliveryModeTest {

    private static MailProperties props(boolean enabled, String host, String from) {
        return new MailProperties(enabled, host, "587", "user", "pass", from,
                true, 7000, 7000, false);
    }

    @Test
    void disabled_whenEnabledFalse_evenWithHostAndFrom() {
        assertThat(MailDeliveryMode.decide(props(false, "smtp.example.com", "no-reply@example.com")))
                .isEqualTo(MailDeliveryMode.DISABLED);
    }

    @Test
    void disabled_whenPropsNull() {
        assertThat(MailDeliveryMode.decide(null)).isEqualTo(MailDeliveryMode.DISABLED);
    }

    @Test
    void smtp_whenEnabledAndHostAndFromPresent() {
        assertThat(MailDeliveryMode.decide(props(true, "smtp.example.com", "no-reply@example.com")))
                .isEqualTo(MailDeliveryMode.SMTP);
    }

    @Test
    void incomplete_whenEnabledButHostNull() {
        assertThat(MailDeliveryMode.decide(props(true, null, "no-reply@example.com")))
                .isEqualTo(MailDeliveryMode.INCOMPLETE);
    }

    @Test
    void incomplete_whenEnabledButHostBlank_emptyStringTreatedAsAbsent() {
        assertThat(MailDeliveryMode.decide(props(true, "   ", "no-reply@example.com")))
                .isEqualTo(MailDeliveryMode.INCOMPLETE);
        // The empty-string case is the one that matters: ${MAIL_HOST:} binds "" not null.
        assertThat(MailDeliveryMode.decide(props(true, "", "no-reply@example.com")))
                .isEqualTo(MailDeliveryMode.INCOMPLETE);
    }

    @Test
    void incomplete_whenEnabledButFromBlank() {
        assertThat(MailDeliveryMode.decide(props(true, "smtp.example.com", "")))
                .isEqualTo(MailDeliveryMode.INCOMPLETE);
    }
}
