package com.erp.assistant.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user in-memory rate limiting for the assistant, with two independent limits (see {@link
 * AssistantProperties.RateLimit}):
 * <ul>
 *   <li>a rolling-hour cap on the number of chat requests, and</li>
 *   <li>a cap on the number of simultaneously active streams.</li>
 * </ul>
 *
 * <p>State is a {@link ConcurrentHashMap} keyed by username; each entry's own monitor guards its counters,
 * so distinct users never contend. This is deliberately node-local (a single-instance demo): it is not a
 * distributed limiter. A caller does {@link #acquire(String)} before starting a stream and must {@link
 * #release(String)} in a {@code finally} once the stream ends (complete/error/timeout), so a concurrency
 * slot is never leaked.
 *
 * <p>{@link #rollbackAcquire(String)} is the one exception to that finally/release contract: it is for the
 * narrow case where {@link #acquire(String)} succeeded (reserving both the hourly slot and the concurrency
 * slot) but the stream never actually started — e.g. the SSE executor's queue was full right after acquiring
 * — so there is nothing for a normal {@link #release(String)} to end. It undoes the whole reservation
 * (hourly timestamp + concurrency slot); callers must use exactly one of {@code release} or {@code
 * rollbackAcquire} for a given {@code acquire}, never both, to avoid double-releasing the concurrency slot.
 */
@Component
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final AssistantProperties.RateLimit limits;
    private final Clock clock;
    private final ConcurrentHashMap<String, UserBucket> buckets = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AssistantRateLimiter(AssistantProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Test seam: inject a fixed/adjustable clock to exercise the rolling window deterministically. */
    AssistantRateLimiter(AssistantProperties properties, Clock clock) {
        this.limits = properties.rateLimit();
        this.clock = clock;
    }

    /**
     * Reserves capacity for one stream: records a chat against the rolling-hour cap and takes a concurrency
     * slot. Throws {@link AssistantRateLimitedException} (→ 429) if either limit is exceeded; nothing is
     * reserved in that case. On success the caller must later call {@link #release(String)}.
     */
    public void acquire(String username) {
        UserBucket bucket = buckets.computeIfAbsent(key(username), k -> new UserBucket());
        long now = clock.millis();
        synchronized (bucket) {
            bucket.evictOlderThan(now - WINDOW.toMillis());
            if (bucket.timestamps.size() >= limits.maxChatsPerHour()) {
                throw new AssistantRateLimitedException(
                        "Hourly chat limit reached (" + limits.maxChatsPerHour() + " per hour). Try again later.",
                        WINDOW.toSeconds());
            }
            if (bucket.active >= limits.maxConcurrentStreams()) {
                throw new AssistantRateLimitedException(
                        "You already have a chat in progress. Wait for it to finish before starting another.", 5L);
            }
            bucket.timestamps.addLast(now);
            bucket.active++;
        }
    }

    /** Releases a concurrency slot taken by {@link #acquire(String)}. Idempotent-safe (never below zero). */
    public void release(String username) {
        UserBucket bucket = buckets.get(key(username));
        if (bucket == null) {
            return;
        }
        synchronized (bucket) {
            if (bucket.active > 0) {
                bucket.active--;
            }
        }
    }

    /**
     * Undoes a successful {@link #acquire(String)} whose stream never started (e.g. the SSE executor
     * rejected the task right after capacity was reserved): removes the hourly-window timestamp {@code
     * acquire} just added — so this attempt does not count against the hourly cap — and frees the
     * concurrency slot. Use this instead of {@link #release(String)} for that case, never both (the
     * concurrency slot would otherwise be freed twice). Idempotent-safe like {@code release}.
     */
    public void rollbackAcquire(String username) {
        UserBucket bucket = buckets.get(key(username));
        if (bucket == null) {
            return;
        }
        synchronized (bucket) {
            bucket.timestamps.pollLast();
            if (bucket.active > 0) {
                bucket.active--;
            }
        }
    }

    private static String key(String username) {
        return username == null || username.isBlank() ? "anonymous" : username;
    }

    /** Per-user counters: the timestamps of recent chats (rolling window) and the active-stream count. */
    private static final class UserBucket {
        final Deque<Long> timestamps = new ArrayDeque<>();
        int active;

        void evictOlderThan(long cutoffMillis) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoffMillis) {
                timestamps.pollFirst();
            }
        }
    }
}
