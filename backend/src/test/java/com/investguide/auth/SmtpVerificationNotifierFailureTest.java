package com.investguide.auth;

import com.investguide.config.AppProperties;
import com.investguide.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SmtpVerificationNotifier} send-failure handling (feature 003, US3 / T019).
 * A transient SMTP failure must NOT propagate (registration is unaffected, FR-006/FR-007) and the
 * failure log must carry the recipient + a reason but NEVER the raw token, password, SMTP
 * credentials, or the raw exception message (FR-003 / SC-005).
 */
class SmtpVerificationNotifierFailureTest {

    private static final Executor INLINE = Runnable::run;

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

    private static MailProperties mailProps() {
        return new MailProperties(true, "smtp.example.com", "587", "smtp-user", "smtp-pass",
                "no-reply@investguide.ua", true, 7000, 7000, false);
    }

    @Test
    void sendFailure_isSwallowed_andLoggedWithoutSecrets() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        // The exception message deliberately echoes the username (as real SMTP auth errors do).
        doThrow(new MailSendException("authentication failed for user smtp-user@host"))
                .when(mailSender).send(any(MimeMessage.class));

        var notifier = new SmtpVerificationNotifier(
                mailSender, new VerificationEmailContent(appProps()), INLINE, mailProps());

        var logs = LogCapture.attach(SmtpVerificationNotifier.class);
        try {
            // FR-006/FR-007: must not propagate to the caller (registration thread).
            assertThatCode(() -> notifier.sendVerification("user@example.com", "SECRET-TOKEN"))
                    .doesNotThrowAnyException();

            String joined = logs.joined();
            assertThat(joined).contains("verification_email_send_failed");
            assertThat(joined).contains("user@example.com");
            assertThat(joined).contains("MailSendException");           // fixed reason = exception class
            assertThat(joined).doesNotContain("SECRET-TOKEN");          // never the raw token
            assertThat(joined).doesNotContain("smtp-pass");             // never the password
            assertThat(joined).doesNotContain("smtp-user");             // never the username / getMessage()
        } finally {
            logs.detach();
        }
    }

    @Test
    void saturatedExecutor_dropsAndLogs_withoutRunningOnCallerThread() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // Executor that always rejects, simulating a saturated bounded queue.
        Executor rejecting = command -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        };
        var notifier = new SmtpVerificationNotifier(
                mailSender, new VerificationEmailContent(appProps()), rejecting, mailProps());

        var logs = LogCapture.attach(SmtpVerificationNotifier.class);
        try {
            assertThatCode(() -> notifier.sendVerification("user@example.com", "SECRET-TOKEN"))
                    .doesNotThrowAnyException();
            assertThat(logs.joined()).contains("verification_email_send_dropped");
            assertThat(logs.joined()).doesNotContain("SECRET-TOKEN");
        } finally {
            logs.detach();
        }
    }
}
