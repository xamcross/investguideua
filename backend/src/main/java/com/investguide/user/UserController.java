package com.investguide.user;

import com.investguide.common.error.ApiException;
import com.investguide.user.dto.UserProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated user profile endpoint (SPECIFICATION §5.1 {@code /me}; ticket BE-A5).
 *
 * <p>Protected by the security chain (any non-public route requires a valid access token), so an
 * unauthenticated call returns {@code 401} before reaching this handler. The principal is the
 * userId placed in the security context by {@code JwtAuthenticationFilter}.
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal String userId) {
        User user = userRepository.findById(userId)
                // Token valid but the user no longer exists (deleted) — treat as unauthorized.
                .orElseThrow(() -> ApiException.unauthorized("Session no longer valid."));
        return UserProfileResponse.from(user);
    }
}
