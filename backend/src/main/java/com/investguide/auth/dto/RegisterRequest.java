package com.investguide.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/register} request body (SPECIFICATION §4.1, §5.2; ticket BE-A2).
 *
 * <p>Password policy mirrors §4.1: at least 8 characters with at least one letter and one digit.
 * An upper length bound is applied to avoid abuse and BCrypt's 72-byte truncation surprise. The
 * email is validated for shape here and lowercased server-side before persistence.
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(max = 254, message = "Email is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password must contain at least one letter and one digit."
        )
        String password
) {
}
