package com.investguide.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/verify} request body (ticket BE-A3). Carries the raw single-use token from
 * the verification link/code.
 */
public record VerifyRequest(
        @NotBlank(message = "Verification token is required.")
        @Size(max = 512, message = "Verification token is malformed.")
        String token
) {
}
