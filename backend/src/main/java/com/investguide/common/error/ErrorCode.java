package com.investguide.common.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical API error codes and their HTTP status mapping (SPECIFICATION §5.3).
 *
 * <p>This enum is the single source of truth for the code→status contract; the global
 * exception handler (X3) and a unit test both derive from it.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INSUFFICIENT_TOKENS(HttpStatus.PAYMENT_REQUIRED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    ADVISOR_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    /** Spec allows 400 or 502; default to 400 (client/pack error). Gateway faults use 502 explicitly. */
    PAYMENT_ERROR(HttpStatus.BAD_REQUEST),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
