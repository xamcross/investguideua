package com.investguide.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X3 DoD: assert the code→status mapping for every defined error code (SPECIFICATION §5.3).
 */
class ErrorCodeMappingTest {

    @Test
    void everyDefinedCodeMapsToItsSpecHttpStatus() {
        Map<ErrorCode, HttpStatus> expected = new EnumMap<>(ErrorCode.class);
        expected.put(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
        expected.put(ErrorCode.EMAIL_TAKEN, HttpStatus.CONFLICT);
        expected.put(ErrorCode.EMAIL_NOT_VERIFIED, HttpStatus.FORBIDDEN);
        expected.put(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        expected.put(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        expected.put(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND);
        expected.put(ErrorCode.INSUFFICIENT_TOKENS, HttpStatus.PAYMENT_REQUIRED);
        expected.put(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
        expected.put(ErrorCode.ADVISOR_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        expected.put(ErrorCode.PAYMENT_ERROR, HttpStatus.BAD_REQUEST);
        expected.put(ErrorCode.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR);

        // Every enum constant must be covered (guards against an unmapped new code).
        assertThat(expected.keySet()).containsExactlyInAnyOrder(ErrorCode.values());
        expected.forEach((code, status) -> assertThat(code.status()).isEqualTo(status));
    }

    @Test
    void envelopeHasExactShape() {
        ErrorResponse r = ErrorResponse.of(ErrorCode.INSUFFICIENT_TOKENS, "no tokens", "req-1");
        assertThat(r.error().code()).isEqualTo("INSUFFICIENT_TOKENS");
        assertThat(r.error().message()).isEqualTo("no tokens");
        assertThat(r.error().requestId()).isEqualTo("req-1");
        assertThat(r.error().details()).isNull();
    }

    @Test
    void apiExceptionStatusOverrideWins() {
        ApiException ex = new ApiException(ErrorCode.PAYMENT_ERROR, "gateway down", HttpStatus.BAD_GATEWAY);
        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.code()).isEqualTo(ErrorCode.PAYMENT_ERROR);
    }
}
