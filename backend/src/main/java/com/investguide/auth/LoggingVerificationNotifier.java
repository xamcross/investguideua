package com.investguide.auth;

import com.investguide.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

/**
 * MVP {@link VerificationNotifier} that logs the verification link instead of sending an email
 * (ticket BE-A3; chosen to avoid an SMTP secret dependency for the MVP).
 *
 * <p>Registered only when no other {@code VerificationNotifier} bean exists, so a real
 * SMTP/provider implementation can be added later without removing this class. The link points at
 * the SPA verify route ({@code <frontendBaseUrl>/verify?token=...}); the raw token is logged here
 * (dev convenience) but never persisted in raw form.
 */
@Component
@ConditionalOnMissingBean(name = "smtpVerificationNotifier")
public class LoggingVerificationNotifier implements VerificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationNotifier.class);

    private final AppProperties appProperties;

    public LoggingVerificationNotifier(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void sendVerification(String email, String rawToken) {
        String link = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl())
                .path("/verify")
                .queryParam("token", rawToken)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
        // MVP: no SMTP. Operators read this from logs to complete verification during testing.
        log.info("verification_email_stub email={} link={}", email, link);
    }
}
