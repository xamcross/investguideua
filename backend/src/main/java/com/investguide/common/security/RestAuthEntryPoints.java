package com.investguide.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.common.error.ErrorCode;
import com.investguide.common.error.ErrorResponse;
import com.investguide.common.error.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Emits the §5.3 error envelope for security-layer rejections so that auth failures look
 * identical to every other API error (consistent {@code requestId}, no stack traces).
 */
@Component
public class RestAuthEntryPoints {

    private final ObjectMapper mapper;

    public RestAuthEntryPoints(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 401 for unauthenticated access to a protected route. */
    public AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) ->
                write(response, ErrorCode.UNAUTHORIZED, "Authentication required.");
    }

    /** 403 for authenticated-but-forbidden access. */
    public AccessDeniedHandler accessDenied() {
        return (request, response, ex) ->
                write(response, ErrorCode.FORBIDDEN, "Access denied.");
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = ErrorResponse.of(code, message, RequestIdFilter.current());
        mapper.writeValue(response.getOutputStream(), body);
    }
}
