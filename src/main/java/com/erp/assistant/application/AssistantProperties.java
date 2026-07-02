package com.erp.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ERP Copilot settings, bound from {@code app.assistant.*} (see application.properties).
 *
 * <p>PR1 is a pure text-chat skeleton. Some fields ({@code selfBaseUrl} and the rate-limit knobs) are
 * defined here so the configuration contract is stable, but are <em>not consumed yet</em> — they land in
 * PR2 (tool calling against the ERP's own read API) and PR3 (rate limiting).
 *
 * @param enabled      master switch. Default {@code false}: with it off the SDK adapter bean is never
 *                     created (so a missing {@code ANTHROPIC_API_KEY} cannot fail startup), and
 *                     {@code POST /api/assistant/chat} returns problem+json.
 * @param model        Claude model id. Adaptive-thinking Opus tier by default.
 * @param maxTokens    per-response output-token ceiling.
 * @param maxTurns     agentic-loop turn cap (PR1 is single-turn; carried for PR2's tool loop).
 * @param selfBaseUrl  base URL the assistant calls back into this ERP's read API (PR2). Defined, unused in PR1.
 * @param rateLimit    per-user rate-limit parameters (PR3). Defined, unused in PR1.
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        boolean enabled,
        String model,
        int maxTokens,
        int maxTurns,
        String selfBaseUrl,
        RateLimit rateLimit) {

    public AssistantProperties {
        if (model == null || model.isBlank()) {
            model = "claude-opus-4-8";
        }
        if (maxTokens <= 0) {
            maxTokens = 4096;
        }
        if (maxTurns <= 0) {
            maxTurns = 8;
        }
        if (selfBaseUrl == null || selfBaseUrl.isBlank()) {
            selfBaseUrl = "http://127.0.0.1:8080";
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(0, 0);
        }
    }

    /**
     * Per-user rate-limit knobs (PR3, unused in PR1). Zero means "unset" — the limiter is a no-op until
     * PR3 wires it in and non-zero defaults are chosen.
     *
     * @param requestsPerMinute max chat requests per user per minute
     * @param burst             short-burst allowance above the steady rate
     */
    public record RateLimit(int requestsPerMinute, int burst) {}
}
