package com.erp.assistant.web;

import com.erp.assistant.application.AgentLoopService;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.AssistantProperties;
import com.erp.assistant.application.AssistantRateLimiter;
import com.erp.assistant.application.ChatModelRequest;
import com.erp.assistant.application.ToolInvoker;
import com.erp.assistant.application.ToolRegistry;
import com.erp.assistant.application.ToolResult;
import com.erp.assistant.application.ToolSpec;
import com.erp.config.GlobalExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@link AssistantController} via standalone MockMvc (no Spring context, no
 * network). A fake {@link AnthropicPort} drives the {@link AgentLoopService}, and the SSE executor runs
 * inline so the async result is available deterministically. Covers: the SSE event sequence
 * (text_delta* → done), the error event, the disabled → problem+json path, and both status states.
 */
class AssistantControllerTest {

    /** Runs SSE work on the calling thread so MockMvc's async dispatch has a completed emitter. */
    private static final Executor INLINE = Runnable::run;

    private static final String CHAT_BODY =
            "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AssistantProperties PROPS =
            new AssistantProperties(true, "claude-opus-4-8", 4096, 8, null, null);

    private static MockMvc mockMvc(Optional<AnthropicPort> port) {
        return mockMvc(port, INLINE);
    }

    private static MockMvc mockMvc(Optional<AnthropicPort> port, Executor executor) {
        ApplicationEventPublisher noopEvents = event -> {};
        ToolInvoker noopInvoker = (ToolSpec spec, JsonNode input, String bearer) -> ToolResult.ok("{}");
        AgentLoopService loop = new AgentLoopService(port, new ToolRegistry(MAPPER), noopInvoker,
                noopEvents, MAPPER, PROPS);
        AssistantRateLimiter limiter = new AssistantRateLimiter(PROPS);
        return MockMvcBuilders
                .standaloneSetup(new AssistantController(loop, limiter, executor))
                // Both advices, matching production: AssistantExceptionHandler for module-specific
                // errors, GlobalExceptionHandler for MethodArgumentNotValidException (bean-validation
                // failures) — needed so the validation tests below see the real problem+json shape.
                .setControllerAdvice(new AssistantExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    // ---- status -------------------------------------------------------------------------------

    @Test
    void statusIsTrueWhenPortWired() throws Exception {
        mockMvc(Optional.of(new TwoDeltaPort()))
                .perform(get("/api/assistant/status"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":true}"));
    }

    @Test
    void statusIsFalseWhenDisabled() throws Exception {
        mockMvc(Optional.empty())
                .perform(get("/api/assistant/status"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":false}"));
    }

    // ---- chat: enabled ------------------------------------------------------------------------

    @Test
    void chatStreamsTextDeltasThenDone() throws Exception {
        MockMvc mvc = mockMvc(Optional.of(new TwoDeltaPort()));

        MvcResult started = mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // SSE frames in order: two text_delta events then a done event carrying usage.
        assertThat(body).contains("event:text_delta");
        assertThat(body).contains("\"text\":\"Hello\"");
        assertThat(body).contains("\"text\":\" world\"");
        assertThat(body).contains("event:done");
        assertThat(body).contains("\"stopReason\":\"end_turn\"");
        assertThat(body).contains("\"outputTokens\":2");
        assertThat(body.indexOf("event:text_delta")).isLessThan(body.indexOf("event:done"));
        assertThat(body).doesNotContain("event:error");
    }

    @Test
    void chatEmitsErrorEventWhenPortFails() throws Exception {
        MockMvc mvc = mockMvc(Optional.of(new FailingPort()));

        MvcResult started = mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mvc.perform(asyncDispatch(started))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("event:error");
        assertThat(body).contains("\"status\":502");
        // The raw exception message must not leak to the client; only the fixed, generic detail does.
        assertThat(body).contains("The assistant could not complete the request");
        assertThat(body).doesNotContain("boom");
    }

    // ---- chat: disabled -> problem+json -------------------------------------------------------

    @Test
    void chatWhileDisabledReturnsProblemJson() throws Exception {
        mockMvc(Optional.empty())
                .perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void chatWhileDisabledWithSseAcceptStillReturnsProblemJson() throws Exception {
        // The SSE client's default Accept header is text/event-stream. Even so, the disabled path must
        // still negotiate to problem+json, not 406 and not a bodiless response.
        mockMvc(Optional.empty())
                .perform(post("/api/assistant/chat")
                        .accept(MediaType.parseMediaType("text/event-stream"))
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---- chat: executor at capacity -------------------------------------------------------------

    @Test
    void chatWhenExecutorSaturatedReturns503ProblemJson() throws Exception {
        Executor rejecting = runnable -> {
            throw new RejectedExecutionException("pool and queue are full");
        };

        mockMvc(Optional.of(new TwoDeltaPort()), rejecting)
                .perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void executorRejectionRollsBackTheHourlySlotSoTheNextChatStillSucceeds() throws Exception {
        // Hourly cap of 1: if the rejected attempt were release()d instead of rolled back, it would still
        // count against the hourly cap and the very next (successful) attempt would 429. Rolling back
        // instead means the failed attempt is fully undone.
        AssistantProperties tightProps = new AssistantProperties(true, "claude-opus-4-8", 4096, 8, null,
                new AssistantProperties.RateLimit(1, 1));
        AgentLoopService loop = new AgentLoopService(Optional.of(new TwoDeltaPort()), new ToolRegistry(MAPPER),
                (spec, input, bearer) -> ToolResult.ok("{}"), event -> {}, MAPPER, tightProps);
        AssistantRateLimiter limiter = new AssistantRateLimiter(tightProps);

        Executor rejecting = runnable -> {
            throw new RejectedExecutionException("pool and queue are full");
        };
        MockMvc rejectingMvc = MockMvcBuilders.standaloneSetup(new AssistantController(loop, limiter, rejecting))
                .setControllerAdvice(new AssistantExceptionHandler(), new GlobalExceptionHandler())
                .build();

        // First attempt: the executor rejects it → 503, and the hourly slot must be rolled back.
        rejectingMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable());

        // Second attempt against the same limiter, now with a working (inline) executor: if the first
        // attempt's hourly slot had leaked, this would 429 instead of streaming normally.
        MockMvc workingMvc = MockMvcBuilders.standaloneSetup(new AssistantController(loop, limiter, INLINE))
                .setControllerAdvice(new AssistantExceptionHandler(), new GlobalExceptionHandler())
                .build();
        MvcResult started = workingMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();
        workingMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }

    // ---- chat: request validation ---------------------------------------------------------------

    @Test
    void chatWithIllegalRoleReturns400() throws Exception {
        String body = "{\"messages\":[{\"role\":\"system\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}";

        mockMvc(Optional.of(new TwoDeltaPort()))
                .perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void chatWithEmptyContentReturns400() throws Exception {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":[]}]}";

        mockMvc(Optional.of(new TwoDeltaPort()))
                .perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void chatWithBlankTextReturns400() throws Exception {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"   \"}]}]}";

        mockMvc(Optional.of(new TwoDeltaPort()))
                .perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void chatWithUnknownPresetReturns400() throws Exception {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}],"
                + "\"preset\":\"not-a-real-preset\"}";

        mockMvc(Optional.of(new TwoDeltaPort()))
                .perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void chatWithReconciliationPresetIsAccepted() throws Exception {
        MockMvc mvc = mockMvc(Optional.of(new TwoDeltaPort()));
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}],"
                + "\"preset\":\"reconciliation\"}";

        MvcResult started = mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }

    // ---- fakes --------------------------------------------------------------------------------

    private static final class TwoDeltaPort implements AnthropicPort {
        @Override
        public void stream(ChatModelRequest request, ChatStreamListener listener) {
            listener.onTextDelta("Hello");
            listener.onTextDelta(" world");
            listener.onEnd(new StopInfo("end_turn", 5L, 2L));
        }
    }

    private static final class FailingPort implements AnthropicPort {
        @Override
        public void stream(ChatModelRequest request, ChatStreamListener listener) {
            listener.onError(new RuntimeException("boom"));
        }
    }
}
