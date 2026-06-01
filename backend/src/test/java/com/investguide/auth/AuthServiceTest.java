package com.investguide.auth;

import com.investguide.auth.dto.LoginRequest;
import com.investguide.auth.dto.RegisterRequest;
import com.investguide.auth.dto.VerifyResponse;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.common.security.JwtService;
import com.investguide.config.AppProperties;
import com.investguide.config.SecurityProperties;
import com.investguide.tokens.TokenLedgerService;
import com.investguide.user.User;
import com.investguide.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService} business logic that does not require a live Mongo
 * (tickets BE-A2, BE-A3, BE-A4). Idempotency/concurrency of the free-token grant itself is a
 * single-document conditional update verified at the repository layer / integration suite (QA1).
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private VerificationTokenRepository verificationTokenRepository;
    private VerificationNotifier verificationNotifier;
    private RefreshTokenService refreshTokenService;
    private TokenLedgerService tokenLedgerService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    private static AppProperties appProps() {
        return new AppProperties(
                "http://localhost:4200",
                new AppProperties.Signup(5),
                new AppProperties.Rate(10, 100, 5),
                new AppProperties.Search(100_000_000L, 5),
                new AppProperties.Pricing(10, 50, 0L, 0.0275, 44.5),
                new AppProperties.Verification(86_400_000L),
                new AppProperties.Cors("http://localhost:4200"));
    }

    private static SecurityProperties secProps() {
        return new SecurityProperties("test-secret-test-secret-test-secret-123456",
                "investguide", 900_000L, 1_209_600_000L,
                new SecurityProperties.RefreshCookie("refresh_token", true, "None", "/api/v1/auth"));
    }

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        verificationTokenRepository = mock(VerificationTokenRepository.class);
        verificationNotifier = mock(VerificationNotifier.class);
        refreshTokenService = mock(RefreshTokenService.class);
        tokenLedgerService = mock(TokenLedgerService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(userRepository, verificationTokenRepository, verificationNotifier,
                refreshTokenService, tokenLedgerService, passwordEncoder, jwtService, appProps(), secProps());
    }

    // ---- BE-A2 ---------------------------------------------------------------------------

    @Test
    void register_lowercasesEmail_createsZeroBalanceUser_andSendsVerification() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u1");
            return u;
        });

        var resp = authService.register(new RegisterRequest("User@Example.com", "Password1"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getValue().getTokenBalance()).isZero();
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(verificationNotifier).sendVerification(eq("user@example.com"), anyString());
        assertThat(resp.emailVerified()).isFalse();
    }

    @Test
    void register_duplicateEmail_throwsEmailTaken() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup@example.com", "Password1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.EMAIL_TAKEN);
        verify(userRepository, never()).save(any());
    }

    // ---- BE-A3 ---------------------------------------------------------------------------

    @Test
    void verify_firstTime_grantsTokens_andReportsFirstVerification() {
        VerificationToken token = VerificationToken.create("u1", "hash", Instant.now().plusSeconds(3600));
        when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokenLedgerService.grantFreeTokens("u1", 5)).thenReturn(true);
        User verified = User.newUnverified("user@example.com", "h");
        verified.setId("u1");
        verified.setEmailVerified(true);
        verified.setTokenBalance(5);
        when(userRepository.findById("u1")).thenReturn(Optional.of(verified));

        VerifyResponse resp = authService.verify("raw-token");

        assertThat(resp.firstVerification()).isTrue();
        assertThat(resp.emailVerified()).isTrue();
        assertThat(resp.tokenBalance()).isEqualTo(5);
    }

    @Test
    void verify_replay_doesNotRegrant() {
        VerificationToken token = VerificationToken.create("u1", "hash", Instant.now().plusSeconds(3600));
        when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokenLedgerService.grantFreeTokens("u1", 5)).thenReturn(false); // already verified
        User verified = User.newUnverified("user@example.com", "h");
        verified.setId("u1");
        verified.setEmailVerified(true);
        verified.setTokenBalance(5);
        when(userRepository.findById("u1")).thenReturn(Optional.of(verified));

        VerifyResponse resp = authService.verify("raw-token");

        assertThat(resp.firstVerification()).isFalse();
        assertThat(resp.tokenBalance()).isEqualTo(5);
    }

    @Test
    void verify_expiredToken_isRejected_andNoGrant() {
        VerificationToken expired = VerificationToken.create("u1", "hash", Instant.now().minusSeconds(10));
        when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verify("raw-token"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(tokenLedgerService, never()).grantFreeTokens(anyString(), anyInt());
    }

    @Test
    void verify_unknownToken_isRejected() {
        when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verify("raw-token"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    // ---- BE-A4 ---------------------------------------------------------------------------

    @Test
    void login_unknownEmail_throwsUnauthorized_noEnumeration() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.newUnverified("user@example.com", "stored-hash");
        user.setId("u1");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("badpass1", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "badpass1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void login_success_returnsAccessToken_andIssuesRefresh() {
        User user = User.newUnverified("user@example.com", "stored-hash");
        user.setId("u1");
        user.setEmailVerified(true);
        user.setTokenBalance(5);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "stored-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(eq("u1"), any())).thenReturn("access-jwt");
        when(refreshTokenService.issue("u1")).thenReturn("refresh-jwt");

        var result = authService.login(new LoginRequest("user@example.com", "Password1"));

        assertThat(result.body().accessToken()).isEqualTo("access-jwt");
        assertThat(result.body().user().tokenBalance()).isEqualTo(5);
        assertThat(result.rawRefreshToken()).isEqualTo("refresh-jwt");
        verify(refreshTokenService).issue("u1");
    }

    @Test
    void refresh_missingToken_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(refreshTokenService, never()).validateAndRevoke(anyString());
    }

    @Test
    void refresh_rotatesAndReturnsNewPair() {
        User user = User.newUnverified("user@example.com", "h");
        user.setId("u1");
        when(refreshTokenService.validateAndRevoke("old-refresh")).thenReturn("u1");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq("u1"), any())).thenReturn("new-access");
        when(refreshTokenService.issue("u1")).thenReturn("new-refresh");

        var result = authService.refresh("old-refresh");

        assertThat(result.body().accessToken()).isEqualTo("new-access");
        assertThat(result.rawRefreshToken()).isEqualTo("new-refresh");
    }
}
