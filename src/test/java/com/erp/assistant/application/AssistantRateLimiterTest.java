package com.erp.assistant.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Unit tests for {@link AssistantRateLimiter}: the hourly chat cap, the concurrent-stream cap, per-user
 * isolation, slot release, and the rolling-window eviction (via an adjustable clock).
 */
class AssistantRateLimiterTest {

    private static AssistantProperties props(int perHour, int concurrent) {
        return new AssistantProperties(true, "m", 10, 4, null,
                new AssistantProperties.RateLimit(perHour, concurrent));
    }

    @Test
    void concurrentStreamCapBlocksASecondSimultaneousStream() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(30, 1));
        limiter.acquire("alice");                 // takes the only slot
        assertThatThrownBy(() -> limiter.acquire("alice"))
                .isInstanceOf(AssistantRateLimitedException.class);
        // Releasing frees the slot for the next stream.
        limiter.release("alice");
        limiter.acquire("alice");                 // ok again
    }

    @Test
    void hourlyCapBlocksAfterLimitEvenWhenStreamsAreReleased() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(3, 1));
        for (int i = 0; i < 3; i++) {
            limiter.acquire("bob");
            limiter.release("bob");               // hourly count still increments
        }
        assertThatThrownBy(() -> limiter.acquire("bob"))
                .isInstanceOf(AssistantRateLimitedException.class);
    }

    @Test
    void limitsArePerUser() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(1, 1));
        limiter.acquire("carol");
        // Carol is at her limit, but dave is unaffected.
        limiter.acquire("dave");
        assertThat(catchThrowableOfType(AssistantRateLimitedException.class, () -> limiter.acquire("carol")))
                .isNotNull();
    }

    @Test
    void rollingWindowEvictsOldChatsSoCapacityReturns() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-03T10:00:00Z"));
        // A clock that reads the current instant from the AtomicReference, so the test can advance time.
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(2, 5), clock);

        limiter.acquire("erin"); limiter.release("erin");
        limiter.acquire("erin"); limiter.release("erin");
        // At the cap within the hour.
        assertThat(catchThrowableOfType(AssistantRateLimitedException.class, () -> limiter.acquire("erin")))
                .isNotNull();

        // Advance beyond the 1-hour window: the earlier chats are evicted and capacity returns.
        now.set(now.get().plus(Duration.ofHours(1).plusMinutes(1)));
        limiter.acquire("erin");                  // ok
    }

    @Test
    void releaseNeverDrivesConcurrencyBelowZero() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(30, 1));
        // Release without a prior acquire is a no-op, not a negative count.
        limiter.release("frank");
        limiter.acquire("frank");
        limiter.release("frank");
        limiter.release("frank");                 // extra release ignored
        limiter.acquire("frank");                 // slot still available
    }

    @Test
    void rollbackAcquireFreesBothTheConcurrencySlotAndTheHourlyTimestamp() {
        // Cap is 1/hour, 1 concurrent. After acquire+rollback, both the hourly count and the concurrency
        // slot must be back to zero — a rolled-back attempt (executor rejected before the stream started)
        // should not count against either limit.
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(1, 1));
        limiter.acquire("gina");
        limiter.rollbackAcquire("gina");

        // Both limits available again: a second acquire succeeds outright (would have thrown if the
        // hourly timestamp or the concurrency slot had leaked).
        limiter.acquire("gina");
    }

    @Test
    void rollbackAcquireNeverDrivesConcurrencyBelowZero() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(props(30, 1));
        // Rollback without a prior acquire is a no-op, not a negative count.
        limiter.rollbackAcquire("hank");
        limiter.acquire("hank");
        limiter.rollbackAcquire("hank");
        limiter.rollbackAcquire("hank");           // extra rollback ignored
        limiter.acquire("hank");                  // slot still available
    }
}
