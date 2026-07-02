package com.erp.assistant;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for ERP Copilot over the real security filter chain (MockMvc, no socket). The assistant
 * flag is OFF here (application.properties default), which is the crucial CI guarantee: with no
 * {@code ANTHROPIC_API_KEY} the app must still boot and answer these endpoints. Verifies:
 * <ul>
 *   <li>{@code /api/assistant/**} is login-gated (401 without a token);</li>
 *   <li>an authenticated guest reads {@code /status} → {@code {enabled:false}} even with the flag off
 *       (the controller is not conditional — only the SDK adapter is);</li>
 *   <li>{@code POST /chat} while disabled answers problem+json (503), not an SSE stream.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AssistantIT {

    private static final String CHAT_BODY =
            "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwt;

    @Test
    void statusRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/assistant/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedGuestReadsStatusDisabled() throws Exception {
        // Guest holds no role; status is login-gated but not role-gated. Flag is off → {enabled:false}.
        mvc.perform(get("/api/assistant/status").header("Authorization", bearer(jwt, "guest")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":false}"));
    }

    @Test
    void chatRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatWhileDisabledReturnsProblemJson() throws Exception {
        // Authenticated but the assistant is off: a clean problem+json 503, not a stream, not a 404.
        mvc.perform(post("/api/assistant/chat").header("Authorization", bearer(jwt, "guest"))
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void chatWhileDisabledWithSseAcceptStillReturnsProblemJson() throws Exception {
        // The SPA's SSE client sends Accept: text/event-stream by default. Through the real security
        // filter chain and content negotiation, the disabled path must still answer problem+json (503),
        // not 406 and not a bodiless response.
        mvc.perform(post("/api/assistant/chat").header("Authorization", bearer(jwt, "guest"))
                        .accept(MediaType.parseMediaType("text/event-stream"))
                        .contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
