package com.erp.assistant;

import com.erp.TestcontainersConfiguration;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.AnthropicSdkAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for the production flag-on wiring: with {@code app.assistant.enabled=true} the REAL
 * {@link AnthropicSdkAdapter} bean must exist and {@code GET /api/assistant/status} must answer
 * {@code {enabled:true}}.
 *
 * <p>This path was never covered before, and it silently broke: a {@code @ConditionalOnMissingBean} on the
 * component-scanned adapter matched the adapter's own just-registered bean definition and vetoed it, so the
 * assistant could not be enabled in production at all — while every other test stayed green (they either
 * kept the flag off or supplied a scripted port, so "adapter absent" always looked intentional).
 *
 * <p>The adapter's constructor reads {@code ANTHROPIC_API_KEY} (no network call); Failsafe injects a dummy
 * value via {@code environmentVariables} in the pom so this test never needs a real key.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.assistant.enabled=true")
@AutoConfigureMockMvc
class AssistantEnabledIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwt;

    @Test
    void flagOnCreatesTheRealSdkAdapterBean() {
        assertThat(context.getBean(AnthropicPort.class)).isInstanceOf(AnthropicSdkAdapter.class);
    }

    @Test
    void statusReportsEnabledTrue() throws Exception {
        mvc.perform(get("/api/assistant/status").header("Authorization", bearer(jwt, "guest")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":true}"));
    }
}
