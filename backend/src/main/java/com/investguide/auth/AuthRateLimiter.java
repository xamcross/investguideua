package com.investguide.auth;

import com.investguide.common.ratelimit.FixedWindowRateLimiter;
import com.investguide.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-IP rate limiting for the public auth endpoints (SPECIFICATION §4.1(3), §8.2, §10; ticket X5).
 *
 * <p>Two independent hourly buckets, both keyed by client IP:
 * <ul>
 *   <li><b>signup bucket</b> — shared by {@code POST /auth/register} and {@code POST /auth/verify},
 *       the account-minting / free-token-grant points, capped at {@code rate.signupPerHourPerIp}
 *       (=10). Register and verify count <em>together</em> so the 11th attempt of either kind from one
 *       IP within the hour is rejected (X5 DoD).</li>
 *   <li><b>refresh bucket</b> — {@code POST /auth/refresh} only, capped at the deliberately looser
 *       {@code rate.refreshPerHourPerIp}. Refresh is intentionally NOT in the signup bucket: a 10/hour
 *       cap behind shared NAT/CGNAT would lock out legitimate token refreshes.</li>
 * </ul>
 *
 * <p>Limits are read from {@link AppProperties} on each call, so they are overridable via config
 * without a rebuild. State is per-instance (documented MVP scope; see {@link FixedWindowRateLimiter}).
 */
@Component
public class AuthRateLimiter {

    private static final long ONE_HOUR_MS = Duration.ofHours(1).toMillis();

    private final AppProperties appProperties;
    private final FixedWindowRateLimiter signupBucket = new FixedWindowRateLimiter(ONE_HOUR_MS);
    private final FixedWindowRateLimiter refreshBucket = new FixedWindowRateLimiter(ONE_HOUR_MS);

    public AuthRateLimiter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Try to consume one permit from the shared register/verify (signup) bucket for {@code clientIp}.
     *
     * @return {@code true} if allowed; {@code false} once {@code rate.signupPerHourPerIp} is reached
     */
    public boolean trySignup(String clientIp) {
        return signupBucket.tryAcquire(clientIp, appProperties.rate().signupPerHourPerIp());
    }

    /**
     * Try to consume one permit from the (looser) refresh bucket for {@code clientIp}.
     *
     * @return {@code true} if allowed; {@code false} once {@code rate.refreshPerHourPerIp} is reached
     */
    public boolean tryRefresh(String clientIp) {
        return refreshBucket.tryAcquire(clientIp, appProperties.rate().refreshPerHourPerIp());
    }
}
