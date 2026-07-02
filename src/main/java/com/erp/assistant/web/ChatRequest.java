package com.erp.assistant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Wire request for {@code POST /api/assistant/chat}.
 *
 * <pre>{@code
 * { "messages": [ { "role": "user", "content": [ { "type": "text", "text": "..." } ] } ] }
 * }</pre>
 *
 * The content-block shape mirrors the Anthropic Messages API so PR2 can add {@code tool_use}/
 * {@code tool_result} block types without a breaking change to this contract. PR1 only handles
 * {@code type == "text"}.
 *
 * <p>Validation cascades through the whole structure ({@code @Valid} on {@code messages} and each turn's
 * {@code content}), so a malformed nested field (bad role, empty content, blank text) fails with the same
 * 400 problem+json as a top-level violation — handled by the shared
 * {@code GlobalExceptionHandler#onValidation}, not re-implemented here.
 *
 * @param messages the conversation, oldest first (at least one turn)
 */
public record ChatRequest(@NotEmpty @Valid List<Message> messages) {

    /**
     * One conversation turn.
     *
     * @param role    {@code "user"} or {@code "assistant"}
     * @param content the turn's content blocks
     */
    public record Message(
            @Pattern(regexp = "user|assistant", message = "must be 'user' or 'assistant'") String role,
            @NotEmpty @Valid List<ContentBlock> content) {}

    /**
     * A content block. PR1: {@code type} is {@code "text"} and {@code text} holds the text. Additional
     * fields (tool ids, inputs, outputs) are added in PR2 for {@code tool_use}/{@code tool_result}.
     *
     * @param type block discriminator (PR1: {@code "text"})
     * @param text text payload for text blocks
     */
    public record ContentBlock(
            @Pattern(regexp = "text", message = "must be 'text'") String type,
            @NotBlank String text) {}
}
