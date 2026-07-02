package com.erp.assistant.application;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The agent loop. PR1 is a single-turn, text-only pass: assemble the system prompt, forward the caller's
 * messages to {@link AnthropicPort#stream}, and relay the streamed events to the caller's listener. PR2
 * grows this into a real tool-calling loop (bounded by {@link AssistantProperties#maxTurns()}), which is
 * why the model access is behind the port rather than called inline.
 *
 * <p>The service is always present (it is not conditional); the SDK-backed {@link AnthropicPort} is
 * optional. When the assistant is disabled there is no port bean, so {@link #isAvailable()} is false and
 * {@link #chat} rejects the call — the controller turns that into problem+json.
 */
@Service
public class AgentLoopService {

    private static final String SYSTEM_PROMPT = """
            You are ERP Copilot, an assistant embedded in a manufacturing ERP system. Help the user
            understand the business data and workflows this ERP manages: the double-entry ledger, inventory,
            procure-to-pay, order-to-cash, manufacturing, HR and reporting. Answer concisely and accurately.
            If a question is outside the ERP's scope, say so briefly.""";

    private final Optional<AnthropicPort> port;

    public AgentLoopService(Optional<AnthropicPort> port) {
        this.port = port;
    }

    /** True when the model port is wired (i.e. {@code app.assistant.enabled=true}). */
    public boolean isAvailable() {
        return port.isPresent();
    }

    /**
     * Runs one text-chat turn, streaming events to {@code listener}. Must only be called when
     * {@link #isAvailable()}; callers guard on that first.
     *
     * @throws IllegalStateException if the assistant is disabled (no port)
     */
    public void chat(List<ChatModelRequest.ChatMessage> messages, AnthropicPort.ChatStreamListener listener) {
        AnthropicPort resolved = port.orElseThrow(() ->
                new IllegalStateException("ERP Copilot is disabled (app.assistant.enabled=false)"));
        resolved.stream(new ChatModelRequest(SYSTEM_PROMPT, messages), listener);
    }
}
