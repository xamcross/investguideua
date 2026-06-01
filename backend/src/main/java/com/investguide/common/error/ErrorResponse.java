package com.investguide.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The exact error envelope shape from SPECIFICATION §5.3:
 * <pre>{ "error": { "code", "message", "requestId", "details"? } }</pre>
 *
 * <p>{@code details} is optional and only populated for aggregated validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, String requestId, Map<String, String> details) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(new Body(code.name(), message, requestId, null));
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId, Map<String, String> details) {
        return new ErrorResponse(new Body(code.name(), message, requestId,
                (details == null || details.isEmpty()) ? null : details));
    }
}
