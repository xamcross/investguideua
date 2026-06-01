package com.investguide.user.dto;

import com.investguide.user.User;

import java.util.List;

/**
 * Public user profile (SPECIFICATION §5.1 {@code /me}; tickets BE-A5, BE-A4).
 *
 * <p>Deliberately excludes {@code passwordHash} and any internal field — this is the only shape
 * the user object is ever serialised to a client. Reused by {@code /me} and the login response.
 */
public record UserProfileResponse(
        String userId,
        String email,
        boolean emailVerified,
        int tokenBalance,
        List<String> roles
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getTokenBalance(),
                user.getRoles());
    }
}
