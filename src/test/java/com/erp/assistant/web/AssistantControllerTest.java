package com.erp.assistant.web;

import com.erp.assistant.application.AgentLoopService;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.ChatModelRequest;
import com.erp.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
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

    private static MockMvc mockMvc(Optional<AnthropicPort> port) {
        return mockMvc(port, INLINE);
    }

    private static MockMvc mockMvc(Optional<AnthropicPort> port, Executor executor) {
        return MockMvcBuilders
                .standaloneSetup(new AssistantController(new AgentLoopService(port), executor))
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
