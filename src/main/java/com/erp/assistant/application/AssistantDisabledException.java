package com.erp.assistant.application;

/**
 * Raised when a chat is requested while ERP Copilot is turned off ({@code app.assistant.enabled=false}).
 * Translated to a 503 problem+json by {@code AssistantExceptionHandler}.
 */
public class AssistantDisabledException extends RuntimeException {
    public AssistantDisabledException(String message) {
        super(message);
    }
}
