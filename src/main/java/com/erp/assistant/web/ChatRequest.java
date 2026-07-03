package com.erp.assistant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Wire request for {@code POST /api/assistant/chat}.
 *
 * <pre>{@code
 * { "messages": [ { "role": "user", "content": [ { "type": "text", "text": "..." } ] } ] }
 * }</pre>
 *
 * The content-block shape mirrors the Anthropic Messages API. Three block types are accepted:
 * <ul>
 *   <li>{@code text} — {@code text} holds the message text;</li>
 *   <li>{@code tool_use} — an assistant tool call replayed from history: {@code id}, {@code name},
 *       {@code input} (an arbitrary JSON object);</li>
 *   <li>{@code tool_result} — a fed-back outcome replayed from history: {@code toolUseId},
 *       {@code content}, optional {@code isError}.</li>
 * </ul>
 * To resume after a write-confirmation pause, the client re-sends the full history (whose last assistant
 * turn contains the write {@code tool_use}) plus a {@link Decision}.
 *
 * <p>An optional {@code preset} selects a specialised analysis system prompt ({@code reconciliation} or
 * {@code margin}; see {@code AgentLoopService}) instead of the general-purpose one. It is stateless like
 * everything else here: the client must resend the same {@code preset} on every resume of a preset-started
 * conversation (including after a write confirmation), or the turn falls back to the general prompt.
 *
 * <p>Validation cascades through the whole structure ({@code @Valid} on {@code messages} and each turn's
 * {@code content}), so a malformed nested field fails with the same 400 problem+json as a top-level
 * violation — handled by the shared {@code GlobalExceptionHandler#onValidation}.
 *
 * @param messages the conversation, oldest first (at least one turn)
 * @param decision optional answer to a pending write-tool confirmation (null on a fresh turn)
 * @param preset   optional analysis preset: {@code reconciliation} or {@code margin} (null for the general
 *                 assistant prompt)
 */
public record ChatRequest(
        @NotEmpty @Valid List<Message> messages,
        @Valid Decision decision,
        @Pattern(regexp = "reconciliation|margin", message = "must be 'reconciliation' or 'margin'") String preset) {

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
     * A content block. {@code type} is one of {@code text}, {@code tool_use}, {@code tool_result}; only the
     * fields relevant to that type are populated. Type-specific requirements (e.g. tool_use needs id/name)
     * are checked in the controller when mapping to the model shape, keeping bean-validation type-agnostic
     * here except for the discriminator itself.
     *
     * @param type      block discriminator: {@code text} | {@code tool_use} | {@code tool_result}
     * @param text      text payload (text blocks)
     * @param id        tool-use id (tool_use blocks)
     * @param name      tool name (tool_use blocks)
     * @param input     tool input as an arbitrary JSON object (tool_use blocks)
     * @param toolUseId the id this result answers (tool_result blocks)
     * @param content   result content (tool_result blocks)
     * @param isError   whether the result is an error (tool_result blocks)
     */
    public record ContentBlock(
            @Pattern(regexp = "text|tool_use|tool_result",
                    message = "must be 'text', 'tool_use' or 'tool_result'") String type,
            String text,
            String id,
            String name,
            tools.jackson.databind.JsonNode input,
            String toolUseId,
            String content,
            Boolean isError) {

        /** A text block must carry non-blank text (kept from PR1's contract). */
        @jakarta.validation.constraints.AssertTrue(message = "text blocks must have non-blank text")
        public boolean isTextPresentForTextBlocks() {
            return !"text".equals(type) || (text != null && !text.isBlank());
        }

        /** A tool_use block must carry a non-blank id and name. */
        @jakarta.validation.constraints.AssertTrue(message = "tool_use blocks must have id and name")
        public boolean isToolUseWellFormed() {
            return !"tool_use".equals(type)
                    || (id != null && !id.isBlank() && name != null && !name.isBlank());
        }

        /** A tool_result block must reference the tool_use id it answers. */
        @jakarta.validation.constraints.AssertTrue(message = "tool_result blocks must have a toolUseId")
        public boolean isToolResultWellFormed() {
            return !"tool_result".equals(type) || (toolUseId != null && !toolUseId.isBlank());
        }
    }

    /**
     * The user's answer to a pending write-tool confirmation.
     *
     * @param toolUseId the write tool_use id being answered (must match the last assistant turn)
     * @param approved  true to execute the write, false to decline
     */
    public record Decision(@NotBlank String toolUseId, @NotNull Boolean approved) {}
}
