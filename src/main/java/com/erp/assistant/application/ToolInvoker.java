package com.erp.assistant.application;

import tools.jackson.databind.JsonNode;

/**
 * Executes a resolved tool call against the ERP's own API and returns the outcome as a {@link ToolResult}.
 * The caller's bearer token is forwarded so the ERP applies exactly the same authorization as any other
 * request from that user — a tool can never do more than its caller could do directly.
 *
 * <p>Implementations must not throw for an HTTP error status: a 4xx/5xx is a normal outcome that is packaged
 * as an {@code ok=false} result (with the problem+json body) and fed back to the model, so it can recover or
 * explain rather than the whole turn failing.
 */
public interface ToolInvoker {

    /**
     * @param spec        the tool being invoked (kind, method, path template)
     * @param input       the model-supplied input (validated against the tool's schema by the model/SDK)
     * @param bearerToken the caller's {@code Authorization: Bearer ...} header value (the raw token, no prefix)
     * @return the result to feed back to the model
     */
    ToolResult invoke(ToolSpec spec, JsonNode input, String bearerToken);
}
