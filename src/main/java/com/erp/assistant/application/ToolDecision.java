package com.erp.assistant.application;

/**
 * The user's answer to a pending write-tool confirmation, sent back with the full history to resume a chat.
 *
 * @param toolUseId the id of the write tool_use block being answered (must match the last assistant turn)
 * @param approved  true to execute the write, false to decline it
 */
public record ToolDecision(String toolUseId, boolean approved) {}
