package com.erp.assistant.web;

import com.erp.assistant.application.AgentEventListener;
import com.erp.assistant.application.AgentLoopService;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.AssistantBusyException;
import com.erp.assistant.application.AssistantDisabledException;
import com.erp.assistant.application.AssistantRateLimiter;
import com.erp.assistant.application.ChatModelRequest;
import com.erp.assistant.application.ToolDecision;
import com.erp.assistant.application.ToolKind;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST surface for ERP Copilot (the AI assistant).
 *
 * <p>The controller is deliberately <em>not</em> conditional on {@code app.assistant.enabled}: {@code GET
 * /status} must answer {@code {enabled:false}} when the assistant is off (so the SPA can hide the UI), and
 * {@code POST /chat} must answer a clean problem+json rather than 404. Only the SDK adapter bean is
 * conditional. Both paths sit behind {@code /api/assistant/**→ authenticated()} (see SecurityConfig).
 *
 * <p>{@code POST /chat} streams Server-Sent Events. Event protocol:
 * <ul>
 *   <li>{@code text_delta}            {@code {"text":"..."}}</li>
 *   <li>{@code tool_call}             {@code {"id","name","input","kind"}} — a tool is being invoked</li>
 *   <li>{@code tool_result}           {@code {"id","ok","result"}} — result (JSON or truncated string)</li>
 *   <li>{@code awaiting_confirmation} {@code {"id","name","input"}} — a write needs user confirmation</li>
 *   <li>{@code done}                  {@code {"stopReason","usage":{...}}} — stopReason may be
 *       {@code end_turn}, {@code max_tokens}, {@code awaiting_confirmation}, {@code max_turns}</li>
 *   <li>{@code error}                 {@code {"title","detail","status"}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    /** No completion timeout: a long generation must not be cut off by the emitter's own deadline. */
    private static final long NO_TIMEOUT = 0L;

    private final AgentLoopService agentLoop;
    private final AssistantRateLimiter rateLimiter;
    private final Executor sseExecutor;

    public AssistantController(AgentLoopService agentLoop, AssistantRateLimiter rateLimiter,
                              Executor assistantSseExecutor) {
        this.agentLoop = agentLoop;
        this.rateLimiter = rateLimiter;
        this.sseExecutor = assistantSseExecutor;
    }

    @GetMapping("/status")
    public AssistantStatus status() {
        return new AssistantStatus(agentLoop.isAvailable());
    }

    // No `produces` on the mapping: SseEmitter sets text/event-stream itself on the streaming path, and
    // leaving it off lets the disabled-branch exceptions render as problem+json.
    @Operation(summary = "Stream a chat turn", description = "Streams the assistant's reply as Server-Sent "
            + "Events (text_delta*, tool_call/tool_result*, then done, awaiting_confirmation or error). See "
            + "the class doc for the event protocol.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/event-stream",
            schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "429", description = "Per-user rate limit exceeded",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "503", description = "Assistant disabled or its executor is at capacity",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/chat")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request,
                           @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (!agentLoop.isAvailable()) {
            // Flag off: a clean problem+json (not an SSE stream, not a 404). → 503.
            throw new AssistantDisabledException(
                    "ERP Copilot is disabled. Set app.assistant.enabled=true to enable it.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        String bearerToken = bearerToken(authHeader);

        List<ChatModelRequest.ChatMessage> messages = toModelMessages(request);
        ToolDecision decision = toDecision(request);
        String preset = request.preset();

        // Reserve rate-limit capacity up front (hourly cap + concurrency slot). Throws → 429 problem+json.
        rateLimiter.acquire(username);

        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        EmitterListener listener = new EmitterListener(emitter);
        try {
            sseExecutor.execute(() -> {
                // Guard the whole background turn: any uncaught throwable becomes an error event, and the
                // concurrency slot is always released.
                try {
                    agentLoop.chat(messages, decision, preset, auth, bearerToken, listener);
                } catch (Throwable ex) {
                    listener.onError(ex);
                } finally {
                    rateLimiter.release(username);
                }
            });
        } catch (RejectedExecutionException ex) {
            // The bounded queue and pool are both full: the stream never started, so undo the whole
            // reservation (hourly timestamp + concurrency slot) rather than release() — this attempt should
            // not count against the hourly cap either — and fail fast with problem+json.
            rateLimiter.rollbackAcquire(username);
            throw new AssistantBusyException(
                    "ERP Copilot is busy handling other requests. Please try again shortly.");
        }
        return emitter;
    }

    /** Extracts the raw bearer token (no {@code Bearer } prefix) from the Authorization header, or null. */
    private static String bearerToken(String authHeader) {
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    /** Maps the wire request to the model-facing message shape, carrying all three content-block types. */
    private static List<ChatModelRequest.ChatMessage> toModelMessages(ChatRequest request) {
        return request.messages().stream()
                .map(m -> new ChatModelRequest.ChatMessage(m.role(),
                        m.content().stream().map(AssistantController::toModelBlock).toList()))
                .toList();
    }

    private static ChatModelRequest.ContentBlock toModelBlock(ChatRequest.ContentBlock b) {
        return switch (b.type()) {
            case "tool_use" -> ChatModelRequest.ContentBlock.toolUse(
                    b.id(), b.name(), b.input() != null ? b.input().toString() : "{}");
            case "tool_result" -> ChatModelRequest.ContentBlock.toolResult(
                    b.toolUseId(), b.content() != null ? b.content() : "",
                    Boolean.TRUE.equals(b.isError()));
            default -> ChatModelRequest.ContentBlock.text(b.text());
        };
    }

    private static ToolDecision toDecision(ChatRequest request) {
        ChatRequest.Decision d = request.decision();
        return d == null ? null : new ToolDecision(d.toolUseId(), Boolean.TRUE.equals(d.approved()));
    }

    /**
     * Bridges the agent loop's events onto the SSE emitter, flushing each frame as it arrives and completing
     * (or completing-with-error) the emitter once. A send failure (client disconnected) completes the
     * emitter and marks the listener cancelled so the loop stops promptly.
     */
    private static final class EmitterListener implements AgentEventListener {

        private final SseEmitter emitter;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        EmitterListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onTextDelta(String text) {
            send("text_delta", new TextDelta(text));
        }

        @Override
        public void onToolCall(String id, String name, ToolKind kind, JsonNode input) {
            send("tool_call", new ToolCall(id, name, input, kind.name().toLowerCase()));
        }

        @Override
        public void onToolResult(String id, boolean ok, JsonNode result) {
            send("tool_result", new ToolResultEvent(id, ok, result));
        }

        @Override
        public void onAwaitingConfirmation(String id, String name, JsonNode input) {
            send("awaiting_confirmation", new AwaitingConfirmation(id, name, input));
        }

        @Override
        public void onEnd(AnthropicPort.StopInfo stop) {
            if (cancelled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("done")
                        .data(new Done(stop.stopReason(), new Usage(stop.inputTokens(), stop.outputTokens()))));
                emitter.complete();
            } catch (IOException | IllegalStateException ex) {
                cancelled.set(true);
                emitter.completeWithError(ex);
            }
        }

        @Override
        public void onError(Throwable error) {
            // Never leak the raw exception to the client — it may carry upstream API details. Log it
            // server-side and send a fixed, generic detail instead.
            log.warn("assistant stream failed", error);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(new ErrorEvent("Assistant request failed",
                                "The assistant could not complete the request. Please try again.",
                                HttpStatus.BAD_GATEWAY.value())));
            } catch (IOException | IllegalStateException ignored) {
                // Best effort: if we cannot send the error frame, just complete.
            } finally {
                emitter.complete();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        /** Sends one SSE frame; a failure marks the stream cancelled and completes-with-error. */
        private void send(String event, Object data) {
            if (cancelled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException ex) {
                cancelled.set(true);
                emitter.completeWithError(ex);
            }
        }
    }

    // --- SSE payload records (serialized as the `data:` JSON of each event) --------------------------

    /** {@code event: text_delta} payload. */
    record TextDelta(String text) {}

    /** {@code event: tool_call} payload. */
    record ToolCall(String id, String name, JsonNode input, String kind) {}

    /** {@code event: tool_result} payload. */
    record ToolResultEvent(String id, boolean ok, JsonNode result) {}

    /** {@code event: awaiting_confirmation} payload. */
    record AwaitingConfirmation(String id, String name, JsonNode input) {}

    /** {@code event: done} payload. */
    record Done(String stopReason, Usage usage) {}

    /** Token usage inside the {@code done} event. */
    record Usage(long inputTokens, long outputTokens) {}

    /** {@code event: error} payload — problem-shaped for consistency with the REST error contract. */
    record ErrorEvent(String title, String detail, int status) {}
}
