package com.investguide.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local-development convenience seeder: inserts a single predefined, ready-to-login user so the
 * authenticated UI (e.g. the Account screen) can be exercised without a live email-verification
 * round-trip.
 *
 * <p><b>Never runs in cloud/production.</b> It is gated by {@code app.seed.dev-user.enabled}, which
 * defaults to {@code false} in {@code application.yml}. Only the local Docker Compose stack sets
 * {@code APP_SEED_DEV_USER_ENABLED=true}. The password below is a well-known fixture, so enabling
 * this anywhere reachable from the internet would create a trivially guessable account -- keep the
 * flag off outside local dev.
 *
 * <p><b>Idempotent:</b> mirrors the existing seeders ({@link com.investguide.tokens.TokenPackSeeder},
 * {@code ProviderSeeder}) -- it skips entirely when a user with the fixture email already exists, so
 * a manually edited balance/password is never clobbered on restart.
 */
@Component
@Order(2) // after TokenPackSeeder(0) and ProviderSeeder(1); ordering is not strictly required here.
@ConditionalOnProperty(name = "app.seed.dev-user.enabled", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    /** Predefined local credentials. Email is stored lowercased per the {@link User} invariant. */
    private static final String DEV_EMAIL = "dev@investguide.local";
    private static final String DEV_PASSWORD = "devpassword123";
    private static final int DEV_TOKEN_BALANCE = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DEV_EMAIL)) {
            log.info("Dev-user seed skipped: {} already exists.", DEV_EMAIL);
            return;
        }

        User user = User.newUnverified(DEV_EMAIL, passwordEncoder.encode(DEV_PASSWORD));
        // Pre-verified with a starting balance so the Account screen shows VERIFIED + tokens.
        user.setEmailVerified(true);
        user.setTokenBalance(DEV_TOKEN_BALANCE);
        user.setRoles(List.of("USER"));

        userRepository.save(user);
        log.info("Dev-user seed complete: created {} (verified, balance={}). LOCAL DEV ONLY.",
                DEV_EMAIL, DEV_TOKEN_BALANCE);
    }
}
