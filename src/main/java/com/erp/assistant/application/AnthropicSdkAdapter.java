package com.erp.assistant.application;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AnthropicPort} implementation over the official Anthropic Java SDK.
 *
 * <p>Created <em>only</em> when {@code app.assistant.enabled=true} <em>and</em> no other
 * {@link AnthropicPort} bean is already defined. That guard is load-bearing:
 * {@link AnthropicOkHttpClient#fromEnv()} throws when {@code ANTHROPIC_API_KEY} is unset, so the client
 * must never be constructed unless the operator has deliberately turned the assistant on (with a key
 * present). With the flag off there is no bean, and {@code POST /api/assistant/chat} answers with
 * problem+json instead. The {@code @ConditionalOnMissingBean} lets an integration test enable the
 * assistant (flag on) while supplying a scripted fake port, so the real SDK client is never built.
 *
 * <p>Per Anthropic's Opus-4.8 request contract: adaptive thinking, no {@code temperature}/{@code top_p}/
 * {@code top_k} (they 400), and the system prompt sent as cache-controlled text blocks (prompt caching).
 *
 * <p>Tool use: the streamed tool_use blocks are accumulated per index (content_block_start carries the id
 * and name; input_json_delta fragments are concatenated across chunks; content_block_stop finalizes the
 * block) and delivered via {@link ChatStreamListener#onToolUse}. History tool_use / tool_result blocks are
 * mapped back onto the matching SDK content-block params so a follow-up turn replays the exact transcript.
 */
@Component
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true")
@ConditionalOnMissingBean(AnthropicPort.class)
public class AnthropicSdkAdapter implements AnthropicPort {

    private final AnthropicClient client;
    private final AssistantProperties properties;
    private final ObjectMapper mapper;

    public AnthropicSdkAdapter(AssistantProperties properties, ObjectMapper mapper) {
        // Reads ANTHROPIC_API_KEY from the environment. Only reached because enabled=true.
        this.client = AnthropicOkHttpClient.fromEnv();
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Test/advanced seam: inject a pre-built client (e.g. a fake) instead of fromEnv(). */
    AnthropicSdkAdapter(AnthropicClient client, AssistantProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public void stream(ChatModelRequest request, ChatStreamListener listener) {
        try {
            MessageCreateParams params = toParams(request);
            long inputTokens = 0L;
            long outputTokens = 0L;
            String stopReason = null;

            // Tool-use blocks accumulate across events, keyed by their content-block index. content_block_start
            // gives id/name; input_json_delta fragments append to partialJson; content_block_stop emits.
            Map<Long, ToolUseAccumulator> toolUses = new HashMap<>();

            boolean cancelled = false;
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
                for (RawMessageStreamEvent event : (Iterable<RawMessageStreamEvent>) stream.stream()::iterator) {
                    // The caller stopped consuming (e.g. client disconnected): stop pulling from the SDK
                    // stream. try-with-resources still closes it below.
                    if (listener.isCancelled()) {
                        cancelled = true;
                        break;
                    }

                    // content_block_start: a text or tool_use block begins. Capture tool_use id/name.
                    if (event.contentBlockStart().isPresent()) {
                        RawContentBlockStartEvent start = event.contentBlockStart().get();
                        start.contentBlock().toolUse().ifPresent(toolUse ->
                                toolUses.put(start.index(),
                                        new ToolUseAccumulator(toolUse.id(), toolUse.name())));
                    }

                    // content_block_delta: text delta → onTextDelta; input_json delta → append to the tool input.
                    if (event.contentBlockDelta().isPresent()) {
                        var deltaEvent = event.contentBlockDelta().get();
                        var delta = deltaEvent.delta();
                        delta.text().ifPresent(textDelta -> listener.onTextDelta(textDelta.text()));
                        delta.inputJson().ifPresent(inputJson -> {
                            ToolUseAccumulator acc = toolUses.get(deltaEvent.index());
                            if (acc != null) {
                                acc.append(inputJson.partialJson());
                            }
                        });
                    }

                    // content_block_stop: if it closed a tool_use block, the input is now complete → emit.
                    if (event.contentBlockStop().isPresent()) {
                        ToolUseAccumulator acc = toolUses.remove(event.contentBlockStop().get().index());
                        if (acc != null) {
                            listener.onToolUse(acc.id, acc.name, acc.inputJsonOrEmpty());
                        }
                    }

                    // Usage arrives across message_start (input) and message_delta (output + stop_reason).
                    if (event.messageStart().isPresent()) {
                        inputTokens = event.messageStart().get().message().usage().inputTokens();
                    }
                    if (event.messageDelta().isPresent()) {
                        var messageDelta = event.messageDelta().get();
                        outputTokens = messageDelta.usage().outputTokens();
                        stopReason = messageDelta.delta().stopReason()
                                .map(Object::toString).orElse(stopReason);
                    }
                }
            }

            if (cancelled) {
                // No onEnd: the caller already stopped listening, and onEnd is meant for a normal
                // completion the caller can act on.
                return;
            }
            listener.onEnd(new StopInfo(stopReason, inputTokens, outputTokens));
        } catch (RuntimeException ex) {
            listener.onError(ex);
        }
    }

    /** Package-private test seam: pure mapping, no SDK call, so it's unit-testable without a fake client. */
    MessageCreateParams toParams(ChatModelRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens((long) properties.maxTokens())
                .thinking(ThinkingConfigAdaptive.builder().build())
                // System prompt as a cache-controlled text block (prompt caching for the stable prefix).
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(request.systemPrompt())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));

        for (Tool tool : request.tools()) {
            builder.addTool(tool);
        }

        for (ChatModelRequest.ChatMessage message : request.messages()) {
            MessageParam.Role role = "assistant".equals(message.role())
                    ? MessageParam.Role.ASSISTANT
                    : MessageParam.Role.USER;
            builder.addMessage(MessageParam.builder()
                    .role(role)
                    .contentOfBlockParams(toBlockParams(message.content()))
                    .build());
        }
        return builder.build();
    }

    /** Maps a turn's content blocks to SDK content-block params, one-for-one by type. */
    private List<ContentBlockParam> toBlockParams(List<ChatModelRequest.ContentBlock> content) {
        List<ContentBlockParam> params = new ArrayList<>();
        for (ChatModelRequest.ContentBlock block : content) {
            switch (block.type()) {
                case "text" -> params.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(block.text() == null ? "" : block.text()).build()));
                case "tool_use" -> params.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                        .id(block.id())
                        .name(block.name())
                        .input(toInput(block.inputJson()))
                        .build()));
                case "tool_result" -> params.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(block.toolUseId())
                        .content(block.content() == null ? "" : block.content())
                        .isError(block.isError())
                        .build()));
                default -> throw new IllegalArgumentException("unknown content block type: " + block.type());
            }
        }
        return params;
    }

    /** Parses a tool-input JSON string into the SDK {@code ToolUseBlockParam.Input} (each field an additional property). */
    private ToolUseBlockParam.Input toInput(String inputJson) {
        ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
        if (inputJson == null || inputJson.isBlank()) {
            return input.build();
        }
        try {
            JsonNode node = mapper.readTree(inputJson);
            if (node.isObject()) {
                for (var entry : node.properties()) {
                    input.putAdditionalProperty(entry.getKey(),
                            JsonValue.from(mapper.convertValue(entry.getValue(), Object.class)));
                }
            }
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("invalid tool_use input JSON", e);
        }
        return input.build();
    }

    /** Accumulates one streamed tool_use block: fixed id/name plus concatenated input JSON fragments. */
    private static final class ToolUseAccumulator {
        private final String id;
        private final String name;
        private final StringBuilder inputJson = new StringBuilder();

        ToolUseAccumulator(String id, String name) {
            this.id = id;
            this.name = name;
        }

        void append(String fragment) {
            if (fragment != null) {
                inputJson.append(fragment);
            }
        }

        String inputJsonOrEmpty() {
            return inputJson.length() == 0 ? "{}" : inputJson.toString();
        }
    }
}
