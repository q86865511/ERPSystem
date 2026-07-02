package com.erp.assistant.web;

import com.erp.assistant.application.AgentLoopService;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.AssistantBusyException;
import com.erp.assistant.application.AssistantDisabledException;
import com.erp.assistant.application.ChatModelRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * <p>{@code POST /chat} streams Server-Sent Events with a fixed protocol (stable across PR2/PR3):
 * <ul>
 *   <li>{@code event: text_delta}  {@code data: {"text":"..."}}</li>
 *   <li>{@code event: done}        {@code data: {"stopReason":"...","usage":{"inputTokens":N,"outputTokens":N}}}</li>
 *   <li>{@code event: error}       {@code data: {"title":"...","detail":"...","status":502}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    /** No completion timeout: a long generation must not be cut off by the emitter's own deadline. */
    private static final long NO_TIMEOUT = 0L;

    private final AgentLoopService agentLoop;
    private final Executor sseExecutor;

    public AssistantController(AgentLoopService agentLoop, Executor assistantSseExecutor) {
        this.agentLoop = agentLoop;
        this.sseExecutor = assistantSseExecutor;
    }

    @GetMapping("/status")
    public AssistantStatus status() {
        return new AssistantStatus(agentLoop.isAvailable());
    }

    // No `produces` on the mapping: SseEmitter sets text/event-stream itself on the streaming path, and
    // leaving it off lets the disabled-branch AssistantDisabledException render as problem+json (a produces
    // constraint would pin error negotiation to text/event-stream and drop the problem content type). For
    // the same reason the springdoc annotations below document both response shapes without a `produces`
    // on the mapping itself.
    @Operation(summary = "Stream a chat turn", description = "Streams the assistant's reply as Server-Sent "
            + "Events (text_delta*, then done or error). See the class doc for the event protocol.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/event-stream",
            schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "503", description = "Assistant disabled or its executor is at capacity",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/chat")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        if (!agentLoop.isAvailable()) {
            // Flag off: a clean problem+json (not an SSE stream, not a 404). Handled by
            // AssistantExceptionHandler → 503 ProblemDetail.
            throw new AssistantDisabledException(
                    "ERP Copilot is disabled. Set app.assistant.enabled=true to enable it.");
        }

        List<ChatModelRequest.ChatMessage> messages = toModelMessages(request);
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        EmitterListener listener = new EmitterListener(emitter);

        try {
            sseExecutor.execute(() -> {
                // Guard the whole background turn: any uncaught throwable here (including the
                // IllegalStateException AgentLoopService throws if the port disappeared mid-flight) must
                // become an error event, not a silently-abandoned emitter.
                try {
                    agentLoop.chat(messages, listener);
                } catch (Throwable ex) {
                    listener.onError(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            // The bounded queue and pool are both full: fail fast with a clean problem+json instead of
            // leaving the caller's connection hanging.
            throw new AssistantBusyException(
                    "ERP Copilot is busy handling other requests. Please try again shortly.");
        }
        return emitter;
    }

    /**
     * Maps the wire request to the model-facing message shape (PR1: text blocks only). {@code role},
     * {@code content} and each block's {@code text} are guaranteed non-null/non-blank by
     * {@link ChatRequest}'s bean validation, so no null-coalescing is needed here.
     */
    private static List<ChatModelRequest.ChatMessage> toModelMessages(ChatRequest request) {
        return request.messages().stream()
                .map(m -> new ChatModelRequest.ChatMessage(m.role(),
                        m.content().stream()
                                .map(b -> ChatModelRequest.ContentBlock.text(b.text()))
                                .toList()))
                .toList();
    }

    /**
     * Bridges the model port's streaming callbacks onto the SSE emitter, flushing each event as it arrives
     * and completing (or completing-with-error) the emitter once. Each SSE send flushes; a send failure
     * (client disconnected) completes the emitter, marks the listener cancelled, and stops further work —
     * {@code AnthropicPort} adapters poll {@link #isCancelled()} between chunks so a dead client doesn't
     * keep the streaming call (and its executor thread) alive.
     */
    private static final class EmitterListener implements AnthropicPort.ChatStreamListener {

        private final SseEmitter emitter;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        EmitterListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onTextDelta(String text) {
            try {
                emitter.send(SseEmitter.event().name("text_delta").data(new TextDelta(text)));
            } catch (IOException | IllegalStateException ex) {
                // Client gone or emitter already completed: stop the stream.
                cancelled.set(true);
                emitter.completeWithError(ex);
            }
        }

        @Override
        public void onEnd(AnthropicPort.StopInfo stop) {
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
            // Never leak the raw exception (message, stack trace, cause chain) to the client — it may
            // carry upstream API details. Log it server-side and send a fixed, generic detail instead.
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
    }

    // --- SSE payload records (serialized as the `data:` JSON of each event) --------------------------

    /** {@code event: text_delta} payload. */
    record TextDelta(String text) {}

    /** {@code event: done} payload. */
    record Done(String stopReason, Usage usage) {}

    /** Token usage inside the {@code done} event. */
    record Usage(long inputTokens, long outputTokens) {}

    /** {@code event: error} payload — problem-shaped for consistency with the REST error contract. */
    record ErrorEvent(String title, String detail, int status) {}
}
