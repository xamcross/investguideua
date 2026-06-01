package com.investguide.common.error;

import org.springframework.http.HttpStatus;

/**
 * Business exception carrying a defined {@link ErrorCode} (SPECIFICATION §5.3).
 *
 * <p>Thrown anywhere in the request flow; the global handler maps it to the error envelope.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus statusOverride;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    /** Use the override only for codes whose status is context-dependent (e.g. PAYMENT_ERROR 502). */
    public ApiException(ErrorCode code, String message, HttpStatus statusOverride) {
        super(message);
        this.code = code;
        this.statusOverride = statusOverride;
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return statusOverride != null ? statusOverride : code.status();
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }
}
