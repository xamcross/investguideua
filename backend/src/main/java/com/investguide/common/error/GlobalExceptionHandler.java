package com.investguide.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception into the §5.3 error envelope with the correct HTTP status.
 *
 * <p>Internal details and stack traces are NEVER leaked to clients (SPECIFICATION §5.3): unknown
 * exceptions become {@code INTERNAL} (500) with a generic message; full detail is logged
 * server-side with the {@code requestId} for correlation.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Business exceptions carry their own code/status. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        String requestId = RequestIdFilter.current();
        log.warn("api_error code={} status={} requestId={} msg={}",
                ex.code(), ex.status().value(), requestId, ex.getMessage());
        return ResponseEntity.status(ex.status())
                .body(ErrorResponse.of(ex.code(), ex.getMessage(), requestId));
    }

    /** Bean-validation failures on @Valid request bodies — aggregate field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fe.getField(),
                    fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
        }
        return validationResponse(details);
    }

    /** Validation on @RequestParam / @PathVariable etc. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                details.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return validationResponse(details);
    }

    /** Malformed JSON / unknown fields (DTOs configured to fail on unknown properties). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return validationResponse(Map.of("body", "Malformed or unexpected request body."));
    }

    /** Spring Security access-denied (authenticated but not allowed). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        String requestId = RequestIdFilter.current();
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN, "Access denied.", requestId));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
        String requestId = RequestIdFilter.current();
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, "Resource not found.", requestId));
    }

    /** Catch-all: never leak internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String requestId = RequestIdFilter.current();
        log.error("internal_error requestId={}", requestId, ex);
        return ResponseEntity.status(ErrorCode.INTERNAL.status())
                .body(ErrorResponse.of(ErrorCode.INTERNAL,
                        "An unexpected error occurred.", requestId));
    }

    private ResponseEntity<ErrorResponse> validationResponse(Map<String, String> details) {
        String requestId = RequestIdFilter.current();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR,
                        "Validation failed.", requestId, details));
    }
}
