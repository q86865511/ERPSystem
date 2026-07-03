package com.erp.assistant;

import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.ChatModelRequest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * A scripted {@link AnthropicPort} for integration tests: instead of calling the real model, it replays
 * queued "turn" scripts, each a {@link Consumer} that drives the listener (text deltas, tool_use blocks,
 * onEnd). Supplied as a bean so it wins over {@code AnthropicSdkAdapter} (which is
 * {@code @ConditionalOnMissingBean(AnthropicPort.class)}), letting a test enable the assistant without an
 * {@code ANTHROPIC_API_KEY}.
 *
 * <p>Each {@code stream} call consumes the next queued script. A test enqueues one script per model turn it
 * expects the loop to make; ids captured from seeded data can be baked into a script since it is a closure.
 */
public class ScriptedAnthropicPort implements AnthropicPort {

    private final Deque<Consumer<ChatStreamListener>> scripts = new ArrayDeque<>();

    /** Queues the next turn's script (FIFO). */
    public void enqueue(Consumer<ChatStreamListener> turn) {
        scripts.addLast(turn);
    }

    /** Clears any pending scripts (call between independent scenarios sharing the bean). */
    public void reset() {
        scripts.clear();
    }

    @Override
    public void stream(ChatModelRequest request, ChatStreamListener listener) {
        Consumer<ChatStreamListener> turn = scripts.pollFirst();
        if (turn == null) {
            listener.onEnd(new StopInfo("end_turn", 0L, 0L));
            return;
        }
        turn.accept(listener);
    }
}
