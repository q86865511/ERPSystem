package com.erp.assistant.application;

import tools.jackson.databind.JsonNode;

/**
 * Events the agent loop emits over the course of a (possibly multi-turn) chat, which the web layer relays as
 * Server-Sent Events. Extends the raw model-streaming callbacks ({@link AnthropicPort.ChatStreamListener})
 * with the tool-loop events: a tool call being made, a tool result coming back, and a write action awaiting
 * the user's confirmation.
 *
 * <p>Terminal callbacks are still {@link #onEnd} (normal completion, including the
 * {@code awaiting_confirmation} pause) or {@link #onError}; {@link #onTextDelta} carries assistant text.
 */
public interface AgentEventListener extends AnthropicPort.ChatStreamListener {

    /** A read/write tool is about to be invoked (after any confirmation). Relayed as SSE {@code tool_call}. */
    void onToolCall(String id, String name, ToolKind kind, JsonNode input);

    /** A tool finished; {@code result} is JSON when parseable, else the (possibly truncated) text. Relayed as {@code tool_result}. */
    void onToolResult(String id, boolean ok, JsonNode result);

    /**
     * A write tool needs the user's confirmation before it runs. The loop emits this, then ends the stream
     * (there is no server-side session): the client re-sends the full history plus a decision to resume.
     * Relayed as SSE {@code awaiting_confirmation}.
     */
    void onAwaitingConfirmation(String id, String name, JsonNode input);
}
