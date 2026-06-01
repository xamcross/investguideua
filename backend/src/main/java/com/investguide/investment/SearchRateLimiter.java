package com.investguide.investment;

import com.investguide.common.ratelimit.FixedWindowRateLimiter;
import com.investguide.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user fixed-window rate limit for {@code POST /investments/search} (SPECIFICATION §4.1(3), §8.2,
 * §10; ticket X5, scoped to the search flow needed by BE-S3).
 *
 * <p>Backed by the shared {@link FixedWindowRateLimiter} primitive (the same component the per-IP auth
 * buckets use). The limit {@code rate.searchPerMinute} is enforced <b>independently of the token
 * balance</b> so it caps abuse cost (LLM spend) even for users with tokens: the orchestrator checks
 * this <em>before</em> the debit, so an over-limit request returns {@code 429 RATE_LIMITED} with <b>no
 * token spent and no LLM call</b> (BE-S3 step 1, X5 DoD).
 *
 * <p>Not applied to {@code /auth/refresh} — this component is only the per-user search bucket; the
 * per-IP signup/refresh buckets live in {@code AuthRateLimiter}.
 */
@Component
public class SearchRateLimiter {

    private final AppProperties appProperties;
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofMinutes(1).toMillis());

    public SearchRateLimiter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Try to consume one search permit for {@code userId} in the current minute window.
     *
     * @return {@code true} if allowed; {@code false} if the per-minute limit is already reached.
     */
    public boolean tryAcquire(String userId) {
        return limiter.tryAcquire(userId, appProperties.rate().searchPerMinute());
    }
}
