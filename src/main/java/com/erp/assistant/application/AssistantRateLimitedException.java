package com.erp.assistant.application;

/**
 * Raised when a caller exceeds an assistant rate limit (hourly chat cap or concurrent-stream cap). Translated
 * to a 429 problem+json by {@code AssistantExceptionHandler}. {@link #retryAfterSeconds()} feeds an optional
 * {@code Retry-After} header.
 */
public class AssistantRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AssistantRateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
