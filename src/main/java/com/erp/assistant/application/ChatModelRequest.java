package com.erp.assistant.application;

import java.util.List;

/**
 * A model-facing chat request, decoupled from the wire {@code ChatRequest} DTO and from the SDK.
 * {@link #systemPrompt()} is assembled by {@link AgentLoopService}; {@link #messages()} is the running
 * conversation. PR1 carries text-only content blocks; the {@link ChatMessage}/{@link ContentBlock} shape
 * is deliberately open so PR2 can add {@code tool_use}/{@code tool_result} blocks without reshaping this.
 *
 * @param systemPrompt the system prompt (never null/blank)
 * @param messages     the conversation turns, oldest first
 */
public record ChatModelRequest(String systemPrompt, List<ChatMessage> messages) {

    /** A conversation turn. {@code role} is {@code "user"} or {@code "assistant"}. */
    public record ChatMessage(String role, List<ContentBlock> content) {}

    /**
     * One content block within a turn. PR1 only produces/consumes {@code type == "text"}; {@code text}
     * is populated for text blocks. Extra fields (tool ids, tool inputs/outputs) arrive in PR2.
     */
    public record ContentBlock(String type, String text) {
        public static ContentBlock text(String text) {
            return new ContentBlock("text", text);
        }
    }
}
