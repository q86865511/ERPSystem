package com.erp.assistant.application;

/**
 * Isolates the Anthropic SDK behind a semantic, event-driven port so the rest of the module never imports
 * {@code com.anthropic.*}. The only implementation that touches the SDK is
 * {@link com.erp.assistant.application.AnthropicSdkAdapter}, which is created only when
 * {@code app.assistant.enabled=true} — keeping the SDK (and its {@code ANTHROPIC_API_KEY} requirement) off
 * the startup path when the assistant is off.
 *
 * <p>The listener callbacks are named for streaming semantics rather than SDK event shapes. The
 * {@code onToolUse(...)} callback fires once per fully-accumulated tool-use block the model emitted in a
 * turn; callers that predate tool use (e.g. test fakes) keep working via its default no-op.
 */
public interface AnthropicPort {

    /**
     * Streams a single model response for {@code request}, invoking {@code listener} as text arrives and
     * once at the end (or on error). Blocks the calling thread until the stream is exhausted; callers run
     * it on a dedicated executor.
     */
    void stream(ChatModelRequest request, ChatStreamListener listener);

    /** Receives streaming events for one model response. Exactly one of onEnd/onError terminates a stream. */
    interface ChatStreamListener {

        /** A chunk of assistant text. May be called many times; concatenate in order. */
        void onTextDelta(String text);

        /**
         * A tool-use block the model produced this turn, delivered once its streamed input JSON is fully
         * accumulated (content_block_start → input_json_delta* → content_block_stop). Fires before
         * {@link #onEnd} for a turn whose {@link StopInfo#stopReason()} is {@code tool_use}. Default no-op
         * so text-only listeners are unaffected.
         *
         * @param id        the tool-use block id (echoed back in the matching tool_result)
         * @param name      the tool name the model called
         * @param inputJson the tool input as a JSON string (may be {@code "{}"} for a no-arg tool)
         */
        default void onToolUse(String id, String name, String inputJson) {}

        /** The response finished normally. */
        void onEnd(StopInfo stop);

        /** The response failed (transport/API error). Terminal; no further callbacks follow. */
        void onError(Throwable error);

        /**
         * Whether the caller has stopped consuming events (e.g. the SSE client disconnected). Adapters
         * should poll this between chunks and stop emitting further callbacks once it turns true, so a
         * dead client doesn't keep a streaming request (and its executor thread) alive for no reason.
         * Default {@code false}: listeners that have no notion of cancellation (e.g. test fakes) behave
         * as before.
         */
        default boolean isCancelled() {
            return false;
        }
    }

    /**
     * Terminal metadata for a completed response.
     *
     * @param stopReason   why generation stopped (e.g. {@code end_turn}, {@code max_tokens}); may be null
     * @param inputTokens  prompt tokens billed
     * @param outputTokens completion tokens billed
     */
    record StopInfo(String stopReason, long inputTokens, long outputTokens) {}
}
