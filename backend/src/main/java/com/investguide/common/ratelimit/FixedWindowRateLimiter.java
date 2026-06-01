package com.investguide.common.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reusable in-memory fixed-window rate limiter (SPECIFICATION §4.1(3), §8.2, §10; ticket X5).
 *
 * <p>One instance represents one bucket with a fixed {@code windowMs}. Callers supply a key (client IP
 * or userId) and the per-window {@code limit}; the first {@code limit} acquisitions inside a window
 * succeed and the rest are rejected until the window rolls over. This is the single shared primitive
 * behind both the per-IP auth buckets ({@code AuthRateLimiter}) and the per-user search bucket
 * ({@code SearchRateLimiter}).
 *
 * <p><b>Scope (MVP):</b> state is per-instance (single-node), which is acceptable for the documented
 * MVP scale; a horizontally-scaled deployment would back this with a shared store (e.g. Redis). The
 * key map is bounded by the number of distinct active keys within a window — the same characteristic
 * the previous search-only limiter had.
 */
public class FixedWindowRateLimiter {

    private final long windowMs;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(long windowMs) {
        this.windowMs = windowMs;
    }

    /**
     * Try to consume one permit for {@code key} in the current window.
     *
     * @param key   the bucket key (e.g. client IP or userId)
     * @param limit the maximum number of permits allowed per window
     * @return {@code true} if allowed; {@code false} if the limit for the current window is reached
     */
    public boolean tryAcquire(String key, int limit) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs >= windowMs) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() <= limit;
    }

    /** A single fixed window: its start time and how many permits have been requested within it. */
    private static final class Window {
        private final long startMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
