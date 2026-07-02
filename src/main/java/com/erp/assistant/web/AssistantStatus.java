package com.erp.assistant.web;

/**
 * Response for {@code GET /api/assistant/status}: whether ERP Copilot is enabled. Lets the SPA show or hide
 * the assistant UI without attempting a chat.
 *
 * @param enabled true when {@code app.assistant.enabled=true} and the model port is wired
 */
public record AssistantStatus(boolean enabled) {}
