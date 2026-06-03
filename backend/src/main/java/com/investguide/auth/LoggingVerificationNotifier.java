package com.investguide.auth;

import com.investguide.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

/**
 * Fallback {@link VerificationNotifier} used when SMTP delivery is disabled or incompletely
 * configured (feature 003). It does NOT send email; it records that delivery is disabled so the
 * account still verifies via the link (which an operator can surface in dev).
 *
 * <p>This is a plain class constructed by {@link MailDeliveryConfig} (NOT a {@code @Component}); the
 * factory owns the single {@link VerificationNotifier} bean, so there is never a duplicate-bean
 * ambiguity. By default the raw verification link is NOT logged (FR-003 / SC-005): only a
 * non-sensitive {@code verification_email_disabled} event is emitted. When {@code mail.log-link=true}
 * (a clearly-named DEV-ONLY override, off by default, never enabled in production) the raw link is
 * logged for local testing - the single explicit SC-005 carve-out.
 */
public class LoggingVerificationNotifier implements VerificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationNotifier.class);

    private final AppProperties appProperties;
    private final boolean logLink;

    public LoggingVerificationNotifier(AppProperties appProperties, boolean logLink) {
        this.appProperties = appProperties;
        this.logLink = logLink;
    }

    @Override
    public void sendVerification(String email, String rawToken) {
        if (logLink) {
            // DEV-ONLY override: surface the raw link so a developer can complete verification locally.
            String link = UriComponentsBuilder
                    .fromUriString(appProperties.frontendBaseUrl())
                    .path("/verify")
                    .queryParam("token", rawToken)
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUriString();
            log.info("verification_email_disabled email={} link={}", email, link);
        } else {
            // Default: never log the raw token (FR-003 / SC-005); record only the non-sensitive event.
            log.info("verification_email_disabled email={} (delivery disabled; no email sent)", email);
        }
    }
}
