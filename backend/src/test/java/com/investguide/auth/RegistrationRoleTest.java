package com.investguide.auth;

import com.investguide.auth.dto.RegisterRequest;
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

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 008-providers-admin-only US3: accounts created through the public registration flow are always
 * {@code ["USER"]} and never {@code ADMIN}, and no self-service step (verification) grants ADMIN.
 *
 * <p>The guarantee already holds in code ({@code User.newUnverified} hard-codes {@code ["USER"]};
 * {@link RegisterRequest} has no role field). These tests pin it against regression:
 * <ul>
 *   <li>FR-006 / SC-001: a registered account is exactly {@code ["USER"]} and lacks {@code ADMIN}.</li>
 *   <li>FR-008: verification does not change the role set.</li>
 *   <li>FR-007: {@link RegisterRequest} exposes no role component a client could use to self-assign.</li>
 * </ul>
 */
class RegistrationRoleTest {

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

    /** Drive register(...) and return the User that was persisted. */
    private User registerAndCapture() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u1");
            return u;
        });

        authService.register(new RegisterRequest("user@example.com", "Password1"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void register_assignsUserRoleOnly_neverAdmin() {
        User saved = registerAndCapture();

        assertThat(saved.getRoles()).containsExactly("USER");
        assertThat(saved.getRoles()).doesNotContain("ADMIN");
    }

    @Test
    void verify_doesNotGrantAdmin_rolesStayUserOnly() {
        User registered = registerAndCapture();

        // The registered account is what a later verification reads back.
        VerificationToken token = VerificationToken.create("u1", "hash", Instant.now().plusSeconds(3600));
        when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokenLedgerService.grantFreeTokens("u1", 5)).thenReturn(true);
        when(userRepository.findById("u1")).thenReturn(Optional.of(registered));

        authService.verify("raw-token");

        // Verification flips emailVerified/balance but must never touch roles (FR-008).
        assertThat(registered.getRoles()).containsExactly("USER");
        assertThat(registered.getRoles()).doesNotContain("ADMIN");
    }

    @Test
    void registerRequest_exposesNoRoleComponent() {
        List<String> components = Arrays.stream(RegisterRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        // Exactly email + password: no role/roles field a client could send to self-assign ADMIN (FR-007).
        assertThat(components).containsExactlyInAnyOrder("email", "password");
        assertThat(components).noneMatch(name -> name.toLowerCase().contains("role"));
    }
}
