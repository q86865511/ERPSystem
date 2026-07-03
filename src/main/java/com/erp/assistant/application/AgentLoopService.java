package com.erp.assistant.application;

import com.erp.assistant.api.AssistantToolExecutedEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The agent loop: a bounded, manual multi-turn tool-calling loop with human-in-the-loop confirmation for
 * write tools.
 *
 * <p>Each iteration streams one model turn through {@link AnthropicPort}. When the model asks to use tools
 * ({@code stop_reason=tool_use}):
 * <ul>
 *   <li><b>read</b> tools are invoked immediately (forwarding the caller's bearer token), their results fed
 *       back as a single follow-up user message, and the loop continues;</li>
 *   <li>a <b>write</b> tool is <em>not</em> executed — the loop emits {@code awaiting_confirmation} and ends
 *       the stream (no server-side session state). The client resumes by re-sending the full history plus a
 *       {@link ToolDecision}; on approval the write runs (and is audited) and the loop continues, on decline
 *       a "User declined this action." result is fed back so the model can move on.</li>
 * </ul>
 * The read/write kind is always taken from the backend manifest ({@link ToolRegistry}), never trusted from
 * the model or client. Every actual tool execution (reads, and confirmed writes) publishes an
 * {@link AssistantToolExecutedEvent} for the audit trail.
 *
 * <p><b>Resume-path hardening.</b> Because there is no server-side session, a resume request is just "the
 * full history plus a {@link ToolDecision}" replayed by the client — so the service re-validates it as if it
 * were untrusted input, rather than assuming the earlier {@code awaiting_confirmation} pause is still valid:
 * <ul>
 *   <li>the referenced {@code tool_use} must exist in the most recent assistant message and must be a
 *       {@link ToolKind#WRITE} tool per the current manifest (a decision cannot be aimed at a read tool);</li>
 *   <li>the caller's current role visibility ({@link ToolRegistry#isVisible}) is re-checked immediately
 *       before executing an approved write — advertising a tool at the start of a turn does not itself
 *       authorize running it later, e.g. after a role change or a forged decision from a caller who was
 *       never offered the tool;</li>
 *   <li><b>replay</b>: if the target {@code tool_use} already has a matching {@code tool_result} anywhere
 *       earlier in the replayed history, the write has already run (or been declined) once — executing it
 *       again would duplicate a side effect (e.g. a second sales order) purely because the client re-sent an
 *       old decision. This is deliberately rejected via the same {@link AgentEventListener#onError} path as
 *       the other resume failures below, rather than silently ignored with a {@code done}: with no session
 *       state the service cannot distinguish "stale replay" from "client bug", and a visible error is safer
 *       than quietly no-op'ing a request the caller believes will run.</li>
 * </ul>
 * Every one of these rejections ends the stream via {@link AgentEventListener#onError} with a fixed,
 * generic exception (never echoing tool names, roles or ids back), matching how the controller already
 * treats {@code onError} — logged server-side, generic detail to the client.
 *
 * <p>The service is always present; the SDK-backed {@link AnthropicPort} is optional. When the assistant is
 * disabled there is no port, so {@link #isAvailable()} is false and {@link #chat} rejects the call.
 */
@Service
public class AgentLoopService {

    private final Optional<AnthropicPort> port;
    private final ToolRegistry toolRegistry;
    private final ToolInvoker toolInvoker;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final int maxTurns;

    public AgentLoopService(Optional<AnthropicPort> port,
                            ToolRegistry toolRegistry,
                            ToolInvoker toolInvoker,
                            ApplicationEventPublisher events,
                            ObjectMapper mapper,
                            AssistantProperties properties) {
        this.port = port;
        this.toolRegistry = toolRegistry;
        this.toolInvoker = toolInvoker;
        this.events = events;
        this.mapper = mapper;
        this.maxTurns = properties.maxTurns();
    }

    /** True when the model port is wired (i.e. {@code app.assistant.enabled=true}). */
    public boolean isAvailable() {
        return port.isPresent();
    }

    /**
     * Runs a chat — one or more model turns with tool calls — streaming events to {@code listener}. Must only
     * be called when {@link #isAvailable()}.
     *
     * @param messages    the conversation so far (oldest first), including any assistant tool_use / user
     *                    tool_result blocks from a paused-then-resumed exchange
     * @param decision    present when resuming after a write-confirmation pause; null on a fresh turn
     * @param preset      optional analysis preset ({@code reconciliation}/{@code margin}) selecting a
     *                    specialised system prompt; null (or unrecognised) uses the general prompt — see
     *                    {@link AssistantPrompts#forPreset}
     * @param auth        the caller's authentication (drives role-based tool filtering)
     * @param bearerToken the caller's bearer token, forwarded to tool invocations
     * @param listener    receives text/tool/confirmation events and exactly one terminal onEnd/onError
     * @throws IllegalStateException if the assistant is disabled (no port)
     */
    public void chat(List<ChatModelRequest.ChatMessage> messages,
                     ToolDecision decision,
                     String preset,
                     Authentication auth,
                     String bearerToken,
                     AgentEventListener listener) {
        AnthropicPort resolved = port.orElseThrow(() ->
                new IllegalStateException("ERP Copilot is disabled (app.assistant.enabled=false)"));

        String systemPrompt = AssistantPrompts.forPreset(preset);
        List<com.anthropic.models.messages.Tool> tools = toolRegistry.toolsFor(auth);
        String actor = auth != null ? auth.getName() : "system";
        List<ChatModelRequest.ChatMessage> convo = new ArrayList<>(messages);

        // Resume path: the user answered a pending write confirmation. Handle it before calling the model —
        // the assistant tool_use turn is already the last message in history.
        if (decision != null) {
            boolean ended = resumeFromDecision(convo, decision, auth, actor, bearerToken, listener);
            if (ended) {
                return;
            }
        }

        for (int turn = 0; turn < maxTurns; turn++) {
            if (listener.isCancelled()) {
                return;
            }
            TurnCapture capture = new TurnCapture(listener);
            resolved.stream(new ChatModelRequest(systemPrompt, convo, tools), capture);

            if (capture.error != null) {
                listener.onError(capture.error);
                return;
            }
            if (listener.isCancelled()) {
                return;
            }

            List<ChatModelRequest.ContentBlock> assistantBlocks = capture.assistantBlocks();
            if (capture.toolUses.isEmpty()) {
                // No tool calls: a plain text answer. Relay the terminal done and finish.
                listener.onEnd(capture.stopOrFallback());
                return;
            }

            // Record the assistant turn (its text + tool_use blocks) so the next turn replays it verbatim.
            convo.add(new ChatModelRequest.ChatMessage("assistant", assistantBlocks));

            // Execute reads; pause on the first write (HITL). Read results are fed back as one user message.
            List<ChatModelRequest.ContentBlock> resultBlocks = new ArrayList<>();
            for (ToolUse toolUse : capture.toolUses) {
                Optional<ToolSpec> spec = toolRegistry.find(toolUse.name());
                if (spec.isEmpty()) {
                    resultBlocks.add(ChatModelRequest.ContentBlock.toolResult(toolUse.id(),
                            "{\"error\":\"unknown tool: " + toolUse.name() + "\"}", true));
                    continue;
                }
                if (spec.get().isWrite()) {
                    // Write: do not execute. Emit awaiting_confirmation and end the stream (no session state).
                    AnthropicPort.StopInfo stop = capture.stopOrFallback();
                    listener.onAwaitingConfirmation(toolUse.id(), toolUse.name(), parse(toolUse.inputJson()));
                    listener.onEnd(new AnthropicPort.StopInfo("awaiting_confirmation",
                            stop.inputTokens(), stop.outputTokens()));
                    return;
                }
                ToolResult result = execute(spec.get(), toolUse, actor, bearerToken, listener);
                resultBlocks.add(ChatModelRequest.ContentBlock.toolResult(
                        toolUse.id(), result.content(), !result.ok()));
            }
            convo.add(new ChatModelRequest.ChatMessage("user", resultBlocks));
        }

        // Turn budget exhausted without a terminal answer: end cleanly so the client isn't left hanging.
        listener.onEnd(new AnthropicPort.StopInfo("max_turns", 0L, 0L));
    }

    /**
     * Handles a resume decision. The last assistant message must contain the tool_use identified by
     * {@code decision.toolUseId}, it must be a {@link ToolKind#WRITE} tool per the current manifest, and it
     * must not already have a {@code tool_result} earlier in the replayed history (see the class doc's
     * replay note) — these checks apply whether the decision approves or declines, since a decline aimed at
     * a non-pending/already-answered call is just as much a client-side inconsistency. On top of that, an
     * <em>approval</em> additionally requires the caller to still be visible for the tool ({@link
     * ToolRegistry#isVisible}) — declining needs no permission check since nothing runs. Any failing check
     * ends the stream via a fixed {@link AgentEventListener#onError}. On approval the write runs (audited)
     * and its result is fed back; on decline a non-error "User declined this action." result is fed back.
     * Either way the loop then continues.
     *
     * @return true if the stream was already ended here (error); false to continue the loop
     */
    private boolean resumeFromDecision(List<ChatModelRequest.ChatMessage> convo, ToolDecision decision,
                                       Authentication auth, String actor, String bearerToken,
                                       AgentEventListener listener) {
        ChatModelRequest.ContentBlock writeBlock = findWriteToolUse(convo, decision.toolUseId());
        if (writeBlock == null) {
            listener.onError(new IllegalStateException("decision does not match a pending write tool call"));
            return true;
        }
        if (alreadyHasResult(convo, decision.toolUseId())) {
            // Replay: this tool_use was already answered once (approved or declined) earlier in the
            // history. Refuse rather than re-run a write a second time from a stale/resent decision.
            listener.onError(new IllegalStateException("decision already answered; refusing to replay it"));
            return true;
        }
        Optional<ToolSpec> specOpt = toolRegistry.find(writeBlock.name());
        if (specOpt.isEmpty() || !specOpt.get().isWrite()) {
            // Manifest no longer knows this tool, or (defensively — findWriteToolUse already filters this
            // out) it is not a write tool. Either way this is not a valid confirmation target.
            listener.onError(new IllegalStateException("decision does not match a pending write tool call"));
            return true;
        }
        ToolSpec spec = specOpt.get();
        if (decision.approved() && !toolRegistry.isVisible(spec, auth)) {
            // The caller's current role does not permit this tool — re-checked at execution time, not just
            // when it was first advertised (guards role changes and forged decisions from ineligible callers).
            listener.onError(new IllegalStateException("caller is not permitted to run this tool"));
            return true;
        }
        List<ChatModelRequest.ContentBlock> resultBlocks = new ArrayList<>();
        if (decision.approved()) {
            ToolUse toolUse = new ToolUse(writeBlock.id(), writeBlock.name(), writeBlock.inputJson());
            ToolResult result = execute(spec, toolUse, actor, bearerToken, listener);
            resultBlocks.add(ChatModelRequest.ContentBlock.toolResult(
                    writeBlock.id(), result.content(), !result.ok()));
        } else {
            // Declined: a normal (non-error) result so the model narrates the decline rather than retrying.
            resultBlocks.add(ChatModelRequest.ContentBlock.toolResult(
                    writeBlock.id(), "User declined this action.", false));
        }
        convo.add(new ChatModelRequest.ChatMessage("user", resultBlocks));
        return false;
    }

    /** Invokes a tool, emits SSE tool_call/tool_result, and publishes the audit event. */
    private ToolResult execute(ToolSpec spec, ToolUse toolUse, String actor, String bearerToken,
                               AgentEventListener listener) {
        JsonNode input = parse(toolUse.inputJson());
        listener.onToolCall(toolUse.id(), spec.name(), spec.kind(), input);
        ToolResult result = toolInvoker.invoke(spec, input, bearerToken);
        listener.onToolResult(toolUse.id(), result.ok(), parseOrText(result.content()));
        events.publishEvent(new AssistantToolExecutedEvent(actor, spec.name(),
                spec.kind().name().toLowerCase(), summarize(input.toString()), result.ok(),
                summarize(result.content())));
        return result;
    }

    /**
     * Finds the tool_use block with {@code id} in the most recent assistant message, restricted to a
     * {@link ToolKind#WRITE} tool per the current manifest — a decision can only ever target a write (reads
     * run immediately and are never paused), so a decision aimed at a read tool_use, an unknown tool, or any
     * tool_use outside the last assistant turn all resolve to null here.
     */
    private ChatModelRequest.ContentBlock findWriteToolUse(List<ChatModelRequest.ChatMessage> convo, String id) {
        for (int i = convo.size() - 1; i >= 0; i--) {
            ChatModelRequest.ChatMessage m = convo.get(i);
            if (!"assistant".equals(m.role())) {
                continue;
            }
            for (ChatModelRequest.ContentBlock block : m.content()) {
                if ("tool_use".equals(block.type()) && id != null && id.equals(block.id())) {
                    return toolRegistry.find(block.name()).filter(ToolSpec::isWrite).isPresent() ? block : null;
                }
            }
            return null; // only the most recent assistant turn is a valid target
        }
        return null;
    }

    /**
     * True when some message in {@code convo} already carries a {@code tool_result} for {@code toolUseId} —
     * i.e. this tool call was already answered earlier in the replayed history. See the class doc's replay
     * note for why this refuses execution rather than silently continuing.
     */
    private static boolean alreadyHasResult(List<ChatModelRequest.ChatMessage> convo, String toolUseId) {
        if (toolUseId == null) {
            return false;
        }
        for (ChatModelRequest.ChatMessage m : convo) {
            for (ChatModelRequest.ContentBlock block : m.content()) {
                if ("tool_result".equals(block.type()) && toolUseId.equals(block.toolUseId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode parse(String json) {
        try {
            return json == null || json.isBlank() ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (JacksonException e) {
            return mapper.createObjectNode();
        }
    }

    /** Result content is JSON when it parses, otherwise a text node (e.g. a truncated string). */
    private JsonNode parseOrText(String content) {
        try {
            return content == null ? mapper.nullNode() : mapper.readTree(content);
        } catch (JacksonException e) {
            return mapper.getNodeFactory().textNode(content);
        }
    }

    private static String summarize(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    /** A single tool call the model made this turn. */
    private record ToolUse(String id, String name, String inputJson) {}

    /**
     * Captures one model turn: relays text deltas live to the outer listener, accumulates the text and the
     * tool_use calls, and records the terminal stop info / error. The accumulated blocks become the assistant
     * message appended to the running conversation.
     */
    private static final class TurnCapture implements AnthropicPort.ChatStreamListener {
        private final AgentEventListener outer;
        private final StringBuilder text = new StringBuilder();
        private final List<ToolUse> toolUses = new ArrayList<>();
        private AnthropicPort.StopInfo stop;
        private Throwable error;

        TurnCapture(AgentEventListener outer) {
            this.outer = outer;
        }

        @Override
        public void onTextDelta(String delta) {
            text.append(delta);
            outer.onTextDelta(delta);
        }

        @Override
        public void onToolUse(String id, String name, String inputJson) {
            toolUses.add(new ToolUse(id, name, inputJson));
        }

        @Override
        public void onEnd(AnthropicPort.StopInfo stopInfo) {
            this.stop = stopInfo;
        }

        @Override
        public void onError(Throwable err) {
            this.error = err;
        }

        @Override
        public boolean isCancelled() {
            return outer.isCancelled();
        }

        /** The assistant turn's content blocks: accumulated text (if any) followed by the tool_use calls. */
        List<ChatModelRequest.ContentBlock> assistantBlocks() {
            List<ChatModelRequest.ContentBlock> blocks = new ArrayList<>();
            if (text.length() > 0) {
                blocks.add(ChatModelRequest.ContentBlock.text(text.toString()));
            }
            for (ToolUse toolUse : toolUses) {
                blocks.add(ChatModelRequest.ContentBlock.toolUse(
                        toolUse.id(), toolUse.name(), toolUse.inputJson()));
            }
            return blocks;
        }

        /**
         * The captured stop info, or a safe {@code "incomplete"}/zero-usage fallback if the port's stream
         * ended without ever calling {@link #onEnd} or {@link #onError} (a misbehaving/incomplete {@link
         * AnthropicPort} implementation) — so the loop can still terminate the SSE stream cleanly instead of
         * throwing a {@link NullPointerException}.
         */
        AnthropicPort.StopInfo stopOrFallback() {
            return stop != null ? stop : new AnthropicPort.StopInfo("incomplete", 0L, 0L);
        }
    }
}
