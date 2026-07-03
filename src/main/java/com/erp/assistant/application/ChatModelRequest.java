package com.erp.assistant.application;

import com.anthropic.models.messages.Tool;

import java.util.List;

/**
 * A model-facing chat request, decoupled from the wire {@code ChatRequest} DTO and from the SDK's request
 * builder. {@link #systemPrompt()} is assembled by {@link AgentLoopService}; {@link #messages()} is the
 * running conversation; {@link #tools()} is the tool catalogue to advertise (already role-filtered by
 * {@link ToolRegistry}). Content blocks carry text, {@code tool_use} (assistant calls) and {@code
 * tool_result} (fed-back outcomes); the adapter maps each to the matching SDK content block.
 *
 * @param systemPrompt the system prompt (never null/blank)
 * @param messages     the conversation turns, oldest first
 * @param tools        the SDK tools to advertise this turn (empty ⇒ no tools)
 */
public record ChatModelRequest(String systemPrompt, List<ChatMessage> messages, List<Tool> tools) {

    /** A conversation turn. {@code role} is {@code "user"} or {@code "assistant"}. */
    public record ChatMessage(String role, List<ContentBlock> content) {}

    /**
     * One content block within a turn, tagged by {@link #type()}:
     * <ul>
     *   <li>{@code "text"} — {@link #text()} holds the text.</li>
     *   <li>{@code "tool_use"} — an assistant tool call: {@link #id()}, {@link #name()}, {@link #inputJson()}
     *       (the input as a JSON string).</li>
     *   <li>{@code "tool_result"} — a fed-back outcome: {@link #toolUseId()}, {@link #content()} (the result
     *       text) and {@link #isError()}.</li>
     * </ul>
     * Unused fields are {@code null}/{@code false} for a given type.
     */
    public record ContentBlock(
            String type,
            String text,
            String id,
            String name,
            String inputJson,
            String toolUseId,
            String content,
            boolean isError) {

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null, null, null, null, false);
        }

        public static ContentBlock toolUse(String id, String name, String inputJson) {
            return new ContentBlock("tool_use", null, id, name, inputJson, null, null, false);
        }

        public static ContentBlock toolResult(String toolUseId, String content, boolean isError) {
            return new ContentBlock("tool_result", null, null, null, null, toolUseId, content, isError);
        }
    }
}
