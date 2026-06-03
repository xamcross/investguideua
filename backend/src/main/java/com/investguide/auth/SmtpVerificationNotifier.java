package com.investguide.auth;

import com.investguide.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Real SMTP {@link VerificationNotifier} (feature 003). Sends the Ukrainian verification email built
 * by {@link VerificationEmailContent}. Constructed by {@link MailDeliveryConfig} only when SMTP is
 * fully configured (NOT a {@code @Component}).
 *
 * <p><b>Non-blocking + failure-isolated.</b> The actual send is submitted to a bounded executor, so a
 * slow/hung SMTP server never blocks or fails the registration response (FR-011); the registration
 * has already returned by the time this runs. Any send failure is caught and logged off-thread - it
 * is never propagated and never silently swallowed (FR-006/FR-007). On queue saturation the submit is
 * dropped-and-logged rather than run on the caller thread, keeping FR-011 absolute (FR-011 / SC-006).
 *
 * <p><b>Secret hygiene (FR-003/SC-005).</b> Failure logs carry only the recipient and the exception
 * <em>class</em> (a fixed reason) - never the raw token, the password, the SMTP credentials, or the
 * raw exception message (some Jakarta Mail auth errors echo the username).
 */
public class SmtpVerificationNotifier implements VerificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationNotifier.class);

    private final JavaMailSender mailSender;
    private final VerificationEmailContent content;
    private final Executor mailExecutor;
    private final String from;

    public SmtpVerificationNotifier(JavaMailSender mailSender,
                                    VerificationEmailContent content,
                                    Executor mailExecutor,
                                    MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.content = content;
        this.mailExecutor = mailExecutor;
        this.from = mailProperties.from();
    }

    @Override
    public void sendVerification(String email, String rawToken) {
        // Build the message body on the caller thread (cheap, no I/O); hand the network send off-thread.
        VerificationEmailContent.Content body = content.build(rawToken);
        try {
            mailExecutor.execute(() -> deliver(email, body));
        } catch (RejectedExecutionException ex) {
            // Bounded queue saturated: drop + log rather than run the send on the registration thread.
            log.error("verification_email_send_dropped email={} reason={}", email, ex.getClass().getSimpleName());
        }
    }

    /** Runs on the mail executor thread. Never throws to the pool; logs a sanitized failure instead. */
    private void deliver(String email, VerificationEmailContent.Content body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(body.subject());
            helper.setText(body.text(), body.html()); // multipart/alternative: plain + HTML
            mailSender.send(message);
            log.info("verification_email_sent email={}", email);
        } catch (Exception ex) {
            // Class + recipient only: never the raw token/password/credentials or ex.getMessage()
            // (some SMTP auth errors echo the username). FR-003 / FR-007 / SC-005.
            log.error("verification_email_send_failed email={} reason={}", email, ex.getClass().getSimpleName());
        }
    }
}
