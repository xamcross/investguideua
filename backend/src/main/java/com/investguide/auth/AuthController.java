package com.investguide.auth;

import com.investguide.auth.dto.AuthResponse;
import com.investguide.auth.dto.LoginRequest;
import com.investguide.auth.dto.RegisterRequest;
import com.investguide.auth.dto.RegisterResponse;
import com.investguide.auth.dto.VerifyRequest;
import com.investguide.auth.dto.VerifyResponse;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (SPECIFICATION §5.1; tickets BE-A2, BE-A3, BE-A4, X5).
 *
 * <p>All routes here are in the security {@code permitAll} set. The refresh token is delivered and
 * read exclusively via the HttpOnly cookie ({@link RefreshCookieManager}) — never the body.
 *
 * <p><b>Per-IP rate limiting (X5):</b> {@code /register} and {@code /verify} share the signup bucket
 * ({@code rate.signupPerHourPerIp}); {@code /refresh} uses its own looser bucket
 * ({@code rate.refreshPerHourPerIp}). The check runs <em>before</em> any work, so an over-limit call
 * returns {@code 429 RATE_LIMITED} without minting an account, sending an email, or rotating a token.
 * {@code /login} is intentionally not throttled here (no account-minting / free-token side effect).
 * The client IP comes from {@code getRemoteAddr()} — with {@code forward-headers-strategy=framework}
 * (application.yml) that resolves the real client IP from the trusted proxy's {@code X-Forwarded-For}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieManager refreshCookieManager;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService,
                          RefreshCookieManager refreshCookieManager,
                          AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.refreshCookieManager = refreshCookieManager;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest http) {
        if (!rateLimiter.trySignup(clientIp(http))) {
            throw rateLimited();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest request,
                                                 HttpServletRequest http) {
        if (!rateLimiter.trySignup(clientIp(http))) {
            throw rateLimited();
        }
        return ResponseEntity.ok(authService.verify(request.token()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        refreshCookieManager.write(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.body());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        if (!rateLimiter.tryRefresh(clientIp(request))) {
            throw rateLimited();
        }
        String rawRefresh = refreshCookieManager.read(request);
        AuthService.LoginResult result = authService.refresh(rawRefresh);
        refreshCookieManager.write(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.body());
    }

    /** Resolve the client IP (already proxy-corrected by {@code forward-headers-strategy=framework}). */
    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }

    private static ApiException rateLimited() {
        return new ApiException(ErrorCode.RATE_LIMITED,
                "Too many attempts. Please wait a while and try again.");
    }
}
