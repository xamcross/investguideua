package com.investguide.auth;

import com.investguide.config.AppProperties;
import com.investguide.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Wires the single {@link VerificationNotifier} bean for feature 003 and emits one descriptive
 * startup log describing the active delivery mode.
 *
 * <p>The selection is done in Java (not by annotation-only conditions) so that all three of these
 * hold simultaneously: (a) an unconfigured/incomplete mail setup NEVER fails startup (FR-004),
 * (b) exactly one descriptive message is logged once at startup (FR-005), and (c) the SMTP sender is
 * either fully wired or not wired at all (no half-configured sender, spec AS-2.3).
 *
 * <p>We deliberately bind our own {@code mail.*} namespace and never set {@code spring.mail.*}, so
 * Spring Boot's {@code MailSenderAutoConfiguration} (gated on {@code spring.mail.host}) stays inactive
 * and cannot create a second {@link JavaMailSender}. The {@code JavaMailSenderImpl} here is built by
 * hand and only in SMTP mode.
 */
@Configuration
public class MailDeliveryConfig {

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryConfig.class);

    /**
     * Bounded executor for off-thread sends (FR-011). Small pool + bounded queue keeps memory/threads
     * bounded under a burst; the default abort policy makes {@code execute(...)} throw on saturation,
     * which {@link SmtpVerificationNotifier} catches and drops-and-logs (it must NOT run on the caller
     * thread - that is why CallerRunsPolicy is intentionally avoided). Orderly shutdown lets in-flight
     * sends finish on container stop.
     */
    @Bean
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        // Leave the default RejectedExecutionHandler (AbortPolicy): saturation -> RejectedExecutionException
        // -> caught + dropped-and-logged off the registration thread.
        executor.initialize();
        return executor;
    }

    /**
     * The single {@link VerificationNotifier} bean. Chooses SMTP vs. logging fallback per
     * {@link MailDeliveryMode#decide} and logs the once-at-startup mode banner.
     */
    @Bean
    public VerificationNotifier verificationNotifier(MailProperties mailProperties,
                                                     AppProperties appProperties,
                                                     VerificationEmailContent content,
                                                     ThreadPoolTaskExecutor mailExecutor) {
        MailDeliveryMode mode = MailDeliveryMode.decide(mailProperties);
        switch (mode) {
            case SMTP -> {
                log.info("mail_delivery_enabled host={} port={} from={}",
                        mailProperties.host(), mailProperties.portAsInt(), mailProperties.from());
                return new SmtpVerificationNotifier(
                        buildMailSender(mailProperties), content, mailExecutor, mailProperties);
            }
            case INCOMPLETE -> {
                log.warn("mail_config_incomplete missing={} (set MAIL_HOST/MAIL_FROM to enable email; "
                                + "registration still works, no email is sent)",
                        missingRequiredKeys(mailProperties));
                return new LoggingVerificationNotifier(appProperties, mailProperties.logLink());
            }
            default -> {
                log.info("mail_delivery_disabled (mail.enabled=false). Set MAIL_ENABLED=true with "
                        + "MAIL_HOST/MAIL_FROM/MAIL_USERNAME/MAIL_PASSWORD to send verification emails.");
                return new LoggingVerificationNotifier(appProperties, mailProperties.logLink());
            }
        }
    }

    /** Build a JavaMailSender from mail.* only (never spring.mail.*), with bounded timeouts. */
    private static JavaMailSender buildMailSender(MailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.host());
        sender.setPort(props.portAsInt());
        sender.setDefaultEncoding("UTF-8");
        boolean hasCredentials = props.username() != null && !props.username().isBlank();
        if (hasCredentials) {
            sender.setUsername(props.username());
            sender.setPassword(props.password());
        }
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.transport.protocol", "smtp");
        mail.put("mail.smtp.auth", String.valueOf(hasCredentials));
        mail.put("mail.smtp.starttls.enable", String.valueOf(props.startTls()));
        mail.put("mail.smtp.connectiontimeout", String.valueOf(props.connectTimeoutMs()));
        mail.put("mail.smtp.timeout", String.valueOf(props.readTimeoutMs()));
        mail.put("mail.smtp.writetimeout", String.valueOf(props.readTimeoutMs()));
        return sender;
    }

    private static String missingRequiredKeys(MailProperties props) {
        List<String> missing = new ArrayList<>(2);
        if (props.host() == null || props.host().isBlank()) {
            missing.add("host");
        }
        if (props.from() == null || props.from().isBlank()) {
            missing.add("from");
        }
        return String.join(",", missing);
    }
}
