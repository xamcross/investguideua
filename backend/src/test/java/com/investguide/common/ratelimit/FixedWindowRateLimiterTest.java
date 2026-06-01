package com.investguide.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FixedWindowRateLimiter} (ticket X5).
 *
 * <p>Covers the per-window cap, key isolation, window rollover, and that the count never over-admits
 * under concurrency (the property that makes the per-IP signup cap a real abuse control, not a hint).
 */
class FixedWindowRateLimiterTest {

    @Test
    void allowsUpToLimit_thenRejects_withinWindow() {
        // Long window so it never rolls over mid-test.
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(60_000);

        assertThat(limiter.tryAcquire("ip-1", 3)).isTrue();   // 1
        assertThat(limiter.tryAcquire("ip-1", 3)).isTrue();   // 2
        assertThat(limiter.tryAcquire("ip-1", 3)).isTrue();   // 3
        assertThat(limiter.tryAcquire("ip-1", 3)).isFalse();  // 4th -> over the limit
        assertThat(limiter.tryAcquire("ip-1", 3)).isFalse();
    }

    @Test
    void keysAreIsolated() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(60_000);

        assertThat(limiter.tryAcquire("ip-a", 1)).isTrue();
        assertThat(limiter.tryAcquire("ip-a", 1)).isFalse(); // ip-a exhausted
        assertThat(limiter.tryAcquire("ip-b", 1)).isTrue();  // ip-b independent
    }

    @Test
    void windowRollover_resetsTheCount() throws InterruptedException {
        // Tiny window: after it elapses, the key gets a fresh allowance.
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(20);

        assertThat(limiter.tryAcquire("ip-1", 1)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 1)).isFalse();
        Thread.sleep(40); // let the window roll over
        assertThat(limiter.tryAcquire("ip-1", 1)).isTrue();
    }

    @Test
    void concurrentAcquire_neverExceedsLimit() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(60_000);
        int limit = 10;
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (limiter.tryAcquire("ip-burst", limit)) {
                    granted.incrementAndGet();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(granted.get()).isEqualTo(limit); // exactly the limit, never more
    }
}
