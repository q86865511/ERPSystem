package com.erp.assistant.application;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AnthropicPort} implementation over the official Anthropic Java SDK.
 *
 * <p>Created <em>only</em> when {@code app.assistant.enabled=true}. That guard is load-bearing:
 * {@link AnthropicOkHttpClient#fromEnv()} throws when {@code ANTHROPIC_API_KEY} is unset, so the client
 * must never be constructed unless the operator has deliberately turned the assistant on (with a key
 * present). With the flag off there is no bean, and {@code POST /api/assistant/chat} answers with
 * problem+json instead.
 *
 * <p>Per Anthropic's Opus-4.8 request contract: adaptive thinking, no {@code temperature}/{@code top_p}/
 * {@code top_k} (they 400), and the system prompt sent as cache-controlled text blocks (prompt caching).
 */
@Component
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true")
public class AnthropicSdkAdapter implements AnthropicPort {

    private final AnthropicClient client;
    private final AssistantProperties properties;

    public AnthropicSdkAdapter(AssistantProperties properties) {
        // Reads ANTHROPIC_API_KEY from the environment. Only reached because enabled=true.
        this.client = AnthropicOkHttpClient.fromEnv();
        this.properties = properties;
    }

    /** Test/advanced seam: inject a pre-built client (e.g. a fake) instead of fromEnv(). */
    AnthropicSdkAdapter(AnthropicClient client, AssistantProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void stream(ChatModelRequest request, ChatStreamListener listener) {
        try {
            MessageCreateParams params = toParams(request);
            long inputTokens = 0L;
            long outputTokens = 0L;
            String stopReason = null;

            boolean cancelled = false;
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
                for (RawMessageStreamEvent event : (Iterable<RawMessageStreamEvent>) stream.stream()::iterator) {
                    // The caller stopped consuming (e.g. client disconnected): stop pulling from the SDK
                    // stream. try-with-resources still closes it below.
                    if (listener.isCancelled()) {
                        cancelled = true;
                        break;
                    }
                    // Text deltas.
                    if (event.contentBlockDelta().isPresent()) {
                        event.contentBlockDelta().get().delta().text()
                                .ifPresent(textDelta -> listener.onTextDelta(textDelta.text()));
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

        for (ChatModelRequest.ChatMessage message : request.messages()) {
            String text = joinText(message.content());
            if ("assistant".equals(message.role())) {
                builder.addAssistantMessage(text);
            } else {
                builder.addUserMessage(text);
            }
        }
        return builder.build();
    }

    /** PR1: flatten a turn's text blocks. PR2 will map tool_use/tool_result blocks to SDK content blocks. */
    private static String joinText(List<ChatModelRequest.ContentBlock> content) {
        StringBuilder sb = new StringBuilder();
        for (ChatModelRequest.ContentBlock block : content) {
            if ("text".equals(block.type()) && block.text() != null) {
                sb.append(block.text());
            }
        }
        return sb.toString();
    }
}
