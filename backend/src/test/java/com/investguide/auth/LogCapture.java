package com.investguide.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tiny test helper that captures log events emitted by a given logger via a Logback
 * {@link ListAppender} (feature 003). Used to assert secret hygiene - e.g. that the raw verification
 * token or SMTP credentials never appear in any log line (FR-003 / SC-005).
 */
final class LogCapture {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    private LogCapture(Logger logger, ListAppender<ILoggingEvent> appender) {
        this.logger = logger;
        this.appender = appender;
    }

    static LogCapture attach(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new LogCapture(logger, appender);
    }

    /** Fully formatted messages (placeholders substituted), one per emitted event. */
    List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    /** All formatted messages joined, for a simple substring assertion. */
    String joined() {
        return String.join("\n", messages());
    }

    void detach() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
