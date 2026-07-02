package com.erp.assistant.application;

/**
 * Raised when the assistant's SSE executor has no capacity left for a new chat (its bounded queue and pool
 * are both full). Translated to a 503 problem+json by {@code AssistantExceptionHandler} — distinct from
 * {@link AssistantDisabledException} (flag off) even though both answer 503: this one means "temporarily
 * overloaded, retry later" rather than "turned off".
 */
public class AssistantBusyException extends RuntimeException {
    public AssistantBusyException(String message) {
        super(message);
    }
}
