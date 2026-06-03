package com.investguide.auth;

import com.investguide.config.MailProperties;

/**
 * Which verification-email delivery behavior is wired at startup (feature 003). The choice is made
 * once, in {@link MailDeliveryConfig}, from {@link MailProperties}; this enum holds the pure,
 * side-effect-free decision so it can be unit-tested in isolation.
 *
 * <p>Completeness is decided with {@link String#isBlank()} (NOT {@code == null}): {@code application
 * .yml} binds {@code ${MAIL_HOST:}} to an empty string, never null, so a {@code null} check would
 * wrongly treat a blank-but-present value as configured.
 */
public enum MailDeliveryMode {

    /** {@code mail.enabled=false} (or absent): no SMTP; wire the logging fallback. */
    DISABLED,

    /** {@code mail.enabled=true} but a required field ({@code host}/{@code from}) is blank: degrade to logging + WARN. */
    INCOMPLETE,

    /** {@code mail.enabled=true} and {@code host}+{@code from} present: wire the real SMTP sender. */
    SMTP;

    /** Pure selection: never throws, never logs, no Spring. */
    public static MailDeliveryMode decide(MailProperties props) {
        if (props == null || !props.enabled()) {
            return DISABLED;
        }
        if (isBlank(props.host()) || isBlank(props.from())) {
            return INCOMPLETE;
        }
        return SMTP;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
