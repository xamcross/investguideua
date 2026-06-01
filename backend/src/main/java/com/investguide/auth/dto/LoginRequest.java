package com.investguide.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/login} request body (ticket BE-A4). Validation here is intentionally minimal
 * (presence only) — credential correctness is checked against the store, and failures return a
 * single generic {@code UNAUTHORIZED} with no user enumeration.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 100)
        String password
) {
}
