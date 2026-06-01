package com.investguide.auth;

import com.investguide.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthRateLimiter} (ticket X5).
 *
 * <p>Asserts the two X5 DoD properties: register and verify share one per-IP signup bucket (so the
 * 11th attempt of <em>either</em> kind trips), and {@code /refresh} is governed by a separate, looser
 * bucket that the signup traffic does not deplete.
 */
class AuthRateLimiterTest {

    /** signupPerHourPerIp=2, refreshPerHourPerIp=3 — small values keep the test readable. */
    private static AppProperties props() {
        return new AppProperties(
                "http://localhost:4200",
                new AppProperties.Signup(5),
                new AppProperties.Rate(2, 3, 5),
                new AppProperties.Search(100_000_000L, 5),
                new AppProperties.Pricing(10, 50, 0L, 0.0275, 44.5),
                new AppProperties.Verification(86_400_000L),
                new AppProperties.Cors("http://localhost:4200"));
    }

    @Test
    void registerAndVerifyShareTheSignupBucket() {
        AuthRateLimiter limiter = new AuthRateLimiter(props());
        String ip = "203.0.113.5";

        assertThat(limiter.trySignup(ip)).isTrue();  // register #1
        assertThat(limiter.trySignup(ip)).isTrue();  // verify   #2
        assertThat(limiter.trySignup(ip)).isFalse(); // 3rd combined attempt -> over signupPerHourPerIp=2
    }

    @Test
    void refreshBucketIsSeparateFromSignup() {
        AuthRateLimiter limiter = new AuthRateLimiter(props());
        String ip = "203.0.113.7";

        // Exhaust the signup bucket entirely.
        assertThat(limiter.trySignup(ip)).isTrue();
        assertThat(limiter.trySignup(ip)).isTrue();
        assertThat(limiter.trySignup(ip)).isFalse();

        // Refresh is unaffected (its own looser bucket): refreshPerHourPerIp=3.
        assertThat(limiter.tryRefresh(ip)).isTrue();
        assertThat(limiter.tryRefresh(ip)).isTrue();
        assertThat(limiter.tryRefresh(ip)).isTrue();
        assertThat(limiter.tryRefresh(ip)).isFalse(); // 4th -> over the refresh limit
    }

    @Test
    void differentIpsAreIndependent() {
        AuthRateLimiter limiter = new AuthRateLimiter(props());

        assertThat(limiter.trySignup("198.51.100.1")).isTrue();
        assertThat(limiter.trySignup("198.51.100.1")).isTrue();
        assertThat(limiter.trySignup("198.51.100.1")).isFalse();
        // A different IP (e.g. a different user, not shared NAT) still has full allowance.
        assertThat(limiter.trySignup("198.51.100.2")).isTrue();
    }
}
