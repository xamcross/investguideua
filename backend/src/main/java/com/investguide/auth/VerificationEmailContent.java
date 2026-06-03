package com.investguide.auth;

import com.investguide.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

/**
 * Builds the Ukrainian verification-email content (feature 003, FR-009). Produces a
 * {@code multipart/alternative}-ready pair of {@code text/plain} + {@code text/html} bodies plus a
 * non-empty subject, each containing the verification link prominently and an explicit expiry note.
 *
 * <p>The link is built exactly as {@link LoggingVerificationNotifier} builds it
 * ({@code <frontendBaseUrl>/verify?token=<rawToken>}), so the emailed link is byte-for-byte what the
 * {@code /verify} endpoint expects (FR-002). UA literals live here in a UTF-8 Java source (Maven
 * {@code project.build.sourceEncoding=UTF-8}); they must never be copied into a Windows-executed
 * script.
 */
@Component
public class VerificationEmailContent {

    private static final String PRODUCT = "InvestGuideUA";

    private final AppProperties appProperties;

    public VerificationEmailContent(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** The verification link carrying the one-time raw token (never persisted in raw form). */
    public String link(String rawToken) {
        return UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl())
                .path("/verify")
                .queryParam("token", rawToken)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    /** Build the subject + plain-text + HTML bodies for the given raw token. */
    public Content build(String rawToken) {
        String link = link(rawToken);
        long hours = Math.max(1, appProperties.verification().tokenTtlMs() / 3_600_000L);

        String subject = "Підтвердьте свою адресу - " + PRODUCT;

        String text = "Вітаємо в " + PRODUCT + "!\n\n"
                + "Щоб активувати акаунт, підтвердьте свою електронну адресу за посиланням:\n"
                + link + "\n\n"
                + "Посилання діє " + hours + " год. Якщо ви не реєструвалися на " + PRODUCT
                + ", просто проігноруйте цей лист.\n";

        String html = "<!DOCTYPE html><html lang=\"uk\"><body style=\"font-family:Arial,sans-serif;color:#1a1a1a\">"
                + "<h1 style=\"font-size:20px\">Вітаємо в " + PRODUCT + "!</h1>"
                + "<p>Щоб активувати акаунт, підтвердьте свою електронну адресу:</p>"
                + "<p><a href=\"" + link + "\" "
                + "style=\"display:inline-block;padding:12px 20px;background:#0b5cff;color:#ffffff;"
                + "text-decoration:none;border-radius:6px\">Підтвердити адресу</a></p>"
                + "<p>Або відкрийте посилання: <br><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p style=\"color:#666;font-size:13px\">Посилання діє " + hours + " год. "
                + "Якщо ви не реєструвалися на " + PRODUCT + ", просто проігноруйте цей лист.</p>"
                + "</body></html>";

        return new Content(subject, text, html, link);
    }

    /** Immutable carrier for the built message parts. No persisted state. */
    public record Content(String subject, String text, String html, String link) {
    }
}
