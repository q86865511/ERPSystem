package com.erp.assistant.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentLoopService} against a fake {@link AnthropicPort} (no SDK, no network).
 * Verifies the single-turn relay: the service forwards the caller's messages under an assembled system
 * prompt and streams the port's events straight through; and the disabled state (no port) is rejected.
 */
class AgentLoopServiceTest {

    @Test
    void isAvailableReflectsWhetherPortIsWired() {
        assertThat(new AgentLoopService(Optional.empty()).isAvailable()).isFalse();
        assertThat(new AgentLoopService(Optional.of(new FakePort())).isAvailable()).isTrue();
    }

    @Test
    void chatRelaysTextDeltasThenDone() {
        FakePort port = new FakePort();
        AgentLoopService service = new AgentLoopService(Optional.of(port));
        RecordingListener listener = new RecordingListener();

        service.chat(List.of(userText("hello")), listener);

        // The port saw the user's message wrapped in a non-blank system prompt.
        assertThat(port.captured.systemPrompt()).isNotBlank();
        assertThat(port.captured.messages()).hasSize(1);
        assertThat(port.captured.messages().get(0).role()).isEqualTo("user");

        // Events relayed in order: text deltas then a single done.
        assertThat(listener.deltas).containsExactly("Hello", " world");
        assertThat(listener.done).isNotNull();
        assertThat(listener.done.stopReason()).isEqualTo("end_turn");
        assertThat(listener.done.outputTokens()).isEqualTo(2L);
        assertThat(listener.error).isNull();
    }

    @Test
    void chatWithoutPortThrows() {
        AgentLoopService service = new AgentLoopService(Optional.empty());
        assertThatThrownBy(() -> service.chat(List.of(userText("hi")), new RecordingListener()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ChatModelRequest.ChatMessage userText(String text) {
        return new ChatModelRequest.ChatMessage("user", List.of(ChatModelRequest.ContentBlock.text(text)));
    }

    /** Deterministic port that emits two deltas and a normal done, capturing the request it received. */
    private static final class FakePort implements AnthropicPort {
        ChatModelRequest captured;

        @Override
        public void stream(ChatModelRequest request, ChatStreamListener listener) {
            this.captured = request;
            listener.onTextDelta("Hello");
            listener.onTextDelta(" world");
            listener.onEnd(new StopInfo("end_turn", 5L, 2L));
        }
    }

    private static final class RecordingListener implements AnthropicPort.ChatStreamListener {
        final List<String> deltas = new ArrayList<>();
        AnthropicPort.StopInfo done;
        Throwable error;

        @Override
        public void onTextDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onEnd(AnthropicPort.StopInfo stop) {
            this.done = stop;
        }

        @Override
        public void onError(Throwable err) {
            this.error = err;
        }
    }
}
