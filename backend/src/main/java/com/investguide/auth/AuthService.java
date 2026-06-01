package com.investguide.auth;

import com.investguide.auth.dto.AuthResponse;
import com.investguide.auth.dto.LoginRequest;
import com.investguide.auth.dto.RegisterRequest;
import com.investguide.auth.dto.RegisterResponse;
import com.investguide.auth.dto.VerifyResponse;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.common.security.JwtService;
import com.investguide.common.security.TokenHashing;
import com.investguide.config.AppProperties;
import com.investguide.config.SecurityProperties;
import com.investguide.tokens.TokenLedgerService;
import com.investguide.user.User;
import com.investguide.user.UserRepository;
import com.investguide.user.dto.UserProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Authentication orchestration (tickets BE-A2 register, BE-A3 verify, BE-A4 login/refresh).
 *
 * <p>Holds no balance logic: all balance mutation goes through {@link TokenLedgerService}
 * (the single source of truth, BE-T2). On verification it calls {@code grantFreeTokens}, a
 * single-document conditional update guarded by the {@code emailVerified} flip, keeping the §7
 * invariant (balance never negative, free grant exactly once) in one place. Passwords are
 * BCrypt-hashed; raw passwords and raw tokens are never logged.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationNotifier verificationNotifier;
    private final RefreshTokenService refreshTokenService;
    private final TokenLedgerService tokenLedgerService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final SecurityProperties securityProperties;

    public AuthService(UserRepository userRepository,
                       VerificationTokenRepository verificationTokenRepository,
                       VerificationNotifier verificationNotifier,
                       RefreshTokenService refreshTokenService,
                       TokenLedgerService tokenLedgerService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppProperties appProperties,
                       SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.verificationNotifier = verificationNotifier;
        this.refreshTokenService = refreshTokenService;
        this.tokenLedgerService = tokenLedgerService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.securityProperties = securityProperties;
    }

    // ---- BE-A2: registration -------------------------------------------------------------

    public RegisterResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());

        // Cheap pre-check for the common case; the unique index is the real guard (race-safe).
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "That email is already registered.");
        }

        User user = User.newUnverified(email, passwordEncoder.encode(request.password()));
        try {
            user = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            // Lost the race against a concurrent registration of the same email.
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "That email is already registered.");
        }

        issueAndSendVerification(user);
        log.info("user_registered userId={}", user.getId());
        return RegisterResponse.created(user.getId(), user.getEmail());
    }

    private void issueAndSendVerification(User user) {
        String rawToken = TokenHashing.generateRawToken();
        Instant expiresAt = Instant.now().plusMillis(appProperties.verification().tokenTtlMs());
        verificationTokenRepository.save(
                VerificationToken.create(user.getId(), TokenHashing.sha256Hex(rawToken), expiresAt));
        verificationNotifier.sendVerification(user.getEmail(), rawToken);
    }

    // ---- BE-A3: verification + idempotent free-token grant -------------------------------

    public VerifyResponse verify(String rawToken) {
        VerificationToken token = verificationTokenRepository
                .findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "This verification link is invalid or has expired."));

        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This verification link has expired. Please request a new one.");
        }

        // Atomic flip+grant via the ledger (single source of truth for balance, BE-T2): true only on
        // the first verification for this user. Replays (token already used, or user already verified
        // by any token) return false -> no extra tokens.
        boolean firstVerification =
                tokenLedgerService.grantFreeTokens(token.getUserId(), appProperties.signup().freeTokens());

        if (!token.isUsed()) {
            token.setUsed(true);
            verificationTokenRepository.save(token);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "This verification link is invalid or has expired."));

        if (firstVerification) {
            log.info("email_verified userId={} tokensGranted={}",
                    user.getId(), appProperties.signup().freeTokens());
        }
        String message = firstVerification
                ? "Email verified. " + appProperties.signup().freeTokens() + " free tokens added."
                : "Email already verified.";
        return new VerifyResponse(true, user.getTokenBalance(), firstVerification, message);
    }

    // ---- BE-A4: login -------------------------------------------------------------------

    /** Authenticate and return the access token + issued raw refresh token (cookie set by web layer). */
    public LoginResult login(LoginRequest request) {
        String email = normaliseEmail(request.email());
        Optional<User> maybeUser = userRepository.findByEmail(email);

        // Constant-ish handling: same generic error whether the email exists or the password is
        // wrong (no user enumeration, §4.1.4 / BE-A4 DoD). Always run a hash comparison so timing
        // does not trivially reveal account existence.
        User user = maybeUser.orElse(null);
        String storedHash = user != null ? user.getPasswordHash() : DUMMY_BCRYPT_HASH;
        boolean passwordOk = passwordEncoder.matches(request.password(), storedHash);
        if (user == null || !passwordOk) {
            throw ApiException.unauthorized("Invalid email or password.");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRoles());
        String rawRefresh = refreshTokenService.issue(user.getId());
        log.info("user_login userId={}", user.getId());
        return new LoginResult(
                new AuthResponse(accessToken, securityProperties.accessTtlMs(), UserProfileResponse.from(user)),
                rawRefresh);
    }

    // ---- BE-A4: refresh (rotation) ------------------------------------------------------

    public LoginResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Missing session. Please sign in again.");
        }
        String userId = refreshTokenService.validateAndRevoke(rawRefreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Session no longer valid."));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRoles());
        String newRefresh = refreshTokenService.issue(user.getId());
        return new LoginResult(
                new AuthResponse(accessToken, securityProperties.accessTtlMs(), UserProfileResponse.from(user)),
                newRefresh);
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Carries the JSON body plus the raw refresh token the controller writes to an HttpOnly cookie. */
    public record LoginResult(AuthResponse body, String rawRefreshToken) {
    }

    // A valid BCrypt hash of a random value, used only to equalise timing for unknown emails.
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa8nQ2nQ4mE2yqHq9p7v0m6m9l3m1bqK";
}
