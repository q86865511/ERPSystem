package com.erp.assistant.application;

import com.erp.assistant.api.AssistantToolExecutedEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentLoopService} against scripted fakes (no SDK, no network, no HTTP). Verifies the
 * human-in-the-loop state machine: a write tool call pauses with {@code awaiting_confirmation} and does not
 * execute; resuming with an approve decision executes the write, audits it, and continues; a decline feeds
 * back a non-error result without executing. Also checks the read-tool loop and the plain-text path.
 */
class AgentLoopServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AssistantProperties PROPS =
            new AssistantProperties(true, "claude-opus-4-8", 4096, 8, null, null);

    @Test
    void isAvailableReflectsWhetherPortIsWired() {
        assertThat(service(Optional.empty(), new RecordingInvoker()).isAvailable()).isFalse();
        assertThat(service(Optional.of(new ScriptedPort()), new RecordingInvoker()).isAvailable()).isTrue();
    }

    @Test
    void chatWithoutPortThrows() {
        AgentLoopService svc = service(Optional.empty(), new RecordingInvoker());
        assertThatThrownBy(() -> svc.chat(List.of(userText("hi")), null, null, "tok", new RecordingListener()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void plainTextTurnRelaysDeltasThenDone() {
        ScriptedPort port = new ScriptedPort();
        port.turns.add(turn -> {
            turn.onTextDelta("Hello");
            turn.onTextDelta(" world");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 5L, 2L));
        });
        RecordingListener listener = new RecordingListener();

        service(Optional.of(port), new RecordingInvoker())
                .chat(List.of(userText("hi")), null, null, "tok", listener);

        assertThat(listener.deltas).containsExactly("Hello", " world");
        assertThat(listener.done.stopReason()).isEqualTo("end_turn");
        assertThat(listener.toolCalls).isEmpty();
    }

    @Test
    void writeToolPausesAwaitingConfirmationWithoutExecuting() {
        ScriptedPort port = new ScriptedPort();
        // Turn 1: the model emits some text then a write tool_use (create_sales_order).
        port.turns.add(turn -> {
            turn.onTextDelta("I will create that order.");
            turn.onToolUse("tu_1", "create_sales_order", "{\"partnerId\":1}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 10L, 4L));
        });
        RecordingInvoker invoker = new RecordingInvoker();
        RecordingListener listener = new RecordingListener();

        service(Optional.of(port), invoker)
                .chat(List.of(userText("make an order")), null, null, "tok", listener);

        // Paused for confirmation, no execution, and the terminal done carries the awaiting_confirmation reason.
        assertThat(listener.awaiting).containsExactly("tu_1:create_sales_order");
        assertThat(invoker.invocations).isEmpty();
        assertThat(listener.done.stopReason()).isEqualTo("awaiting_confirmation");
        // Exactly one model turn ran (the loop stopped at the write).
        assertThat(port.consumed).isEqualTo(1);
    }

    @Test
    void approveDecisionExecutesWriteAuditsAndContinues() {
        ScriptedPort port = new ScriptedPort();
        // After the write result is fed back, the model narrates success and ends.
        port.turns.add(turn -> {
            turn.onTextDelta("Done — created sales order.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 12L, 6L));
        });
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.nextResult = ToolResult.ok("{\"id\":42,\"status\":\"DRAFT\"}");
        List<AssistantToolExecutedEvent> audits = new ArrayList<>();
        RecordingListener listener = new RecordingListener();

        // History replays the assistant turn that contains the pending write tool_use.
        List<ChatModelRequest.ChatMessage> history = List.of(
                userText("make an order"),
                new ChatModelRequest.ChatMessage("assistant", List.of(
                        ChatModelRequest.ContentBlock.text("I will create that order."),
                        ChatModelRequest.ContentBlock.toolUse("tu_1", "create_sales_order", "{\"partnerId\":1}"))));

        ApplicationEventPublisher capture = event -> {
            if (event instanceof AssistantToolExecutedEvent e) {
                audits.add(e);
            }
        };
        Authentication sales = new UsernamePasswordAuthenticationToken("alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_SALES")));
        service(Optional.of(port), invoker, capture)
                .chat(history, new ToolDecision("tu_1", true), sales, "tok", listener);

        // The write ran once, was audited, then the model produced the closing text.
        assertThat(invoker.invocations).containsExactly("create_sales_order");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).toolName()).isEqualTo("create_sales_order");
        assertThat(audits.get(0).kind()).isEqualTo("write");
        assertThat(audits.get(0).ok()).isTrue();
        assertThat(listener.deltas).containsExactly("Done — created sales order.");
        assertThat(listener.done.stopReason()).isEqualTo("end_turn");
    }

    @Test
    void declineDecisionFeedsBackWithoutExecuting() {
        ScriptedPort port = new ScriptedPort();
        port.turns.add(turn -> {
            turn.onTextDelta("Okay, I won't create it.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 8L, 3L));
        });
        RecordingInvoker invoker = new RecordingInvoker();
        RecordingListener listener = new RecordingListener();

        List<ChatModelRequest.ChatMessage> history = List.of(
                userText("make an order"),
                new ChatModelRequest.ChatMessage("assistant", List.of(
                        ChatModelRequest.ContentBlock.toolUse("tu_1", "create_sales_order", "{\"partnerId\":1}"))));

        service(Optional.of(port), invoker)
                .chat(history, new ToolDecision("tu_1", false), null, "tok", listener);

        assertThat(invoker.invocations).isEmpty();
        assertThat(listener.done.stopReason()).isEqualTo("end_turn");
        assertThat(listener.deltas).containsExactly("Okay, I won't create it.");
    }

    @Test
    void decisionTargetingAReadToolIsRejectedWithoutExecuting() {
        // The decision names a tool_use whose manifest kind is READ (get_inventory_status), not a pending
        // write — e.g. a client bug or a forged decision. Must be refused, not executed.
        ScriptedPort port = new ScriptedPort();
        RecordingInvoker invoker = new RecordingInvoker();
        RecordingListener listener = new RecordingListener();

        List<ChatModelRequest.ChatMessage> history = List.of(
                userText("what is in stock?"),
                new ChatModelRequest.ChatMessage("assistant", List.of(
                        ChatModelRequest.ContentBlock.toolUse("tu_read", "get_inventory_status", "{}"))));

        service(Optional.of(port), invoker)
                .chat(history, new ToolDecision("tu_read", true), null, "tok", listener);

        assertThat(invoker.invocations).isEmpty();
        assertThat(listener.error).isNotNull();
        assertThat(listener.done).isNull();
        assertThat(port.consumed).isEqualTo(0);
    }

    @Test
    void decisionWithUnknownToolUseIdFailsCleanlyWithoutNpe() {
        ScriptedPort port = new ScriptedPort();
        RecordingInvoker invoker = new RecordingInvoker();
        RecordingListener listener = new RecordingListener();

        List<ChatModelRequest.ChatMessage> history = List.of(
                userText("make an order"),
                new ChatModelRequest.ChatMessage("assistant", List.of(
                        ChatModelRequest.ContentBlock.toolUse("tu_1", "create_sales_order", "{\"partnerId\":1}"))));

        // "tu_does_not_exist" matches nothing in the last assistant turn.
        service(Optional.of(port), invoker)
                .chat(history, new ToolDecision("tu_does_not_exist", true), null, "tok", listener);

        assertThat(invoker.invocations).isEmpty();
        assertThat(listener.error).isNotNull();
        assertThat(listener.done).isNull();
    }

    @Test
    void callerWithoutRequiredRoleForgingApprovedWriteDecisionIsRejected() {
        // create_sales_order requires SALES (tools.json requiredRoles, mirroring SecurityConfig). A caller with an unrelated
        // role (or the role-less guest) cannot get it to execute just by sending an approved decision —
        // the assistant layer must catch this itself, not rely on the downstream REST call's own 403.
        ScriptedPort port = new ScriptedPort();
        RecordingInvoker invoker = new RecordingInvoker();
        RecordingListener listener = new RecordingListener();

        List<ChatModelRequest.ChatMessage> history = List.of(
                userText("make an order"),
                new ChatModelRequest.ChatMessage("assistant", List.of(
                        ChatModelRequest.ContentBlock.toolUse("tu_1", "create_sales_order", "{\"partnerId\":1}"))));

        Authentication warehouseOnly = new UsernamePasswordAuthenticationToken("wanda", null,
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE")));

        service(Optional.of(port), invoker)
                .chat(history, new ToolDecision("tu_1", true), warehouseOnly, "tok", listener);

        assertThat(invoker.invocations).isEmpty();
        assertThat(listener.error).isNotNull();
        assertThat(listener.done).isNull();
    }

    @Test
    void readToolExecutesAndFeedsResultBackThenContinues() {
        ScriptedPort port = new ScriptedPort();
        // Turn 1: call a read tool. Turn 2: answer using its result.
        port.turns.add(turn -> {
            turn.onToolUse("tu_r", "get_inventory_status", "{}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 9L, 2L));
        });
        port.turns.add(turn -> {
            turn.onTextDelta("You have stock.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 20L, 4L));
        });
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.nextResult = ToolResult.ok("[{\"sku\":\"WIDGET\",\"onHand\":10}]");
        RecordingListener listener = new RecordingListener();

        service(Optional.of(port), invoker)
                .chat(List.of(userText("what is in stock?")), null, null, "tok", listener);

        assertThat(invoker.invocations).containsExactly("get_inventory_status");
        assertThat(listener.toolCalls).containsExactly("tu_r:get_inventory_status:read");
        assertThat(listener.toolResults).containsExactly("tu_r:true");
        assertThat(listener.done.stopReason()).isEqualTo("end_turn");
        assertThat(port.consumed).isEqualTo(2);
    }

    // ---- helpers / fakes ------------------------------------------------------------------------

    private static AgentLoopService service(Optional<AnthropicPort> port, ToolInvoker invoker) {
        return service(port, invoker, e -> {});
    }

    private static AgentLoopService service(Optional<AnthropicPort> port, ToolInvoker invoker,
                                            ApplicationEventPublisher publisher) {
        return new AgentLoopService(port, new ToolRegistry(MAPPER), invoker, publisher, MAPPER, PROPS);
    }

    private static ChatModelRequest.ChatMessage userText(String text) {
        return new ChatModelRequest.ChatMessage("user", List.of(ChatModelRequest.ContentBlock.text(text)));
    }

    /** Emits scripted turns in order; each turn is a consumer that drives the listener. */
    private static final class ScriptedPort implements AnthropicPort {
        final List<java.util.function.Consumer<ChatStreamListener>> turns = new ArrayList<>();
        int consumed = 0;

        @Override
        public void stream(ChatModelRequest request, ChatStreamListener listener) {
            if (consumed >= turns.size()) {
                listener.onEnd(new StopInfo("end_turn", 0L, 0L));
                return;
            }
            turns.get(consumed++).accept(listener);
        }
    }

    /** Records tool invocations and returns a canned result. */
    private static final class RecordingInvoker implements ToolInvoker {
        final List<String> invocations = new ArrayList<>();
        ToolResult nextResult = ToolResult.ok("{}");

        @Override
        public ToolResult invoke(ToolSpec spec, JsonNode input, String bearerToken) {
            invocations.add(spec.name());
            return nextResult;
        }
    }

    private static final class RecordingListener implements AgentEventListener {
        final List<String> deltas = new ArrayList<>();
        final List<String> toolCalls = new ArrayList<>();
        final List<String> toolResults = new ArrayList<>();
        final List<String> awaiting = new ArrayList<>();
        AnthropicPort.StopInfo done;
        Throwable error;

        @Override
        public void onTextDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onToolCall(String id, String name, ToolKind kind, JsonNode input) {
            toolCalls.add(id + ":" + name + ":" + kind.name().toLowerCase());
        }

        @Override
        public void onToolResult(String id, boolean ok, JsonNode result) {
            toolResults.add(id + ":" + ok);
        }

        @Override
        public void onAwaitingConfirmation(String id, String name, JsonNode input) {
            awaiting.add(id + ":" + name);
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
