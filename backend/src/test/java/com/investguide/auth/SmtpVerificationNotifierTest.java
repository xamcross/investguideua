package com.investguide.auth;

import com.investguide.config.AppProperties;
import com.investguide.config.MailProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SmtpVerificationNotifier} (feature 003, US1 / T008). Uses a mocked
 * {@link JavaMailSender} and an inline executor so the off-thread send runs synchronously and
 * deterministically. Asserts the built message and that the raw token never reaches the logs.
 */
class SmtpVerificationNotifierTest {

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
    void sendVerification_buildsMultipartMessage_withRecipientSubjectAndExpiringLink() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        var notifier = new SmtpVerificationNotifier(
                mailSender, new VerificationEmailContent(appProps()), INLINE, mailProps());

        notifier.sendVerification("user@example.com", "RAW-TOKEN-123");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage msg = sent.getValue();

        assertThat(msg.getFrom()[0].toString()).isEqualTo("no-reply@investguide.ua");
        assertThat(msg.getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString())
                .isEqualTo("user@example.com");
        assertThat(msg.getSubject()).contains("InvestGuideUA");
        // multipart (plain + html). NOTE: the Content-Type *header* is only written by saveChanges()
        // during a real send (mocked here), so assert on the content object itself. MimeMessageHelper
        // nests the alternative under a mixed/related root, so assert via the recursively-extracted
        // text that BOTH a plain-only and an html-only marker are present.
        assertThat(msg.getContent()).isInstanceOf(Multipart.class);

        String body = extractText(msg);
        assertThat(body).contains("<a href=");                 // html part present
        assertThat(body).contains("Підтвердити адресу");        // html-only call-to-action label
        // FR-002: the emailed link is exactly the /verify link the endpoint expects (in both parts).
        assertThat(body).contains("http://localhost:4200/verify?token=RAW-TOKEN-123");
        // FR-009: explicitly indicates the link expires (assert the expiry phrasing, not a bare digit).
        assertThat(body).contains("Посилання діє");
        assertThat(body).contains("24 год");
    }

    @Test
    void sendVerification_doesNotLogRawToken() {
        var logs = LogCapture.attach(SmtpVerificationNotifier.class);
        try {
            JavaMailSender mailSender = mock(JavaMailSender.class);
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
            var notifier = new SmtpVerificationNotifier(
                    mailSender, new VerificationEmailContent(appProps()), INLINE, mailProps());

            notifier.sendVerification("user@example.com", "SUPER-SECRET-TOKEN");

            assertThat(logs.messages()).isNotEmpty();
            assertThat(logs.joined()).doesNotContain("SUPER-SECRET-TOKEN");
        } finally {
            logs.detach();
        }
    }

    /** Recursively concatenate the text of all body parts of a MIME message. */
    private static String extractText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                sb.append(extractText(mp.getBodyPart(i)));
            }
            return sb.toString();
        }
        return "";
    }
}
