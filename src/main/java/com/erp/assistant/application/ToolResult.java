package com.erp.assistant.application;

/**
 * The outcome of invoking a tool, fed back to the model as a {@code tool_result} block. {@code content} is
 * always a string (JSON text for a successful read/write, or a problem-shaped message for an error) — the
 * model treats it as data, not instructions. {@code ok} false means the call failed (e.g. a 4xx/5xx from
 * the ERP API); it is surfaced to the model with {@code is_error=true} so it can recover or explain.
 *
 * @param ok        whether the call succeeded
 * @param content   the result payload as text (truncated if oversized; see {@link #TRUNCATION_NOTICE})
 * @param truncated whether {@code content} was truncated
 */
public record ToolResult(boolean ok, String content, boolean truncated) {

    /** Results larger than this (in characters) are truncated before being sent back to the model. */
    public static final int MAX_CONTENT_CHARS = 8 * 1024;

    static final String TRUNCATION_NOTICE =
            "\n\n[truncated: result exceeded " + MAX_CONTENT_CHARS + " characters]";

    public static ToolResult ok(String content) {
        return truncate(true, content);
    }

    public static ToolResult error(String content) {
        return truncate(false, content);
    }

    private static ToolResult truncate(boolean ok, String content) {
        String body = content == null ? "" : content;
        if (body.length() <= MAX_CONTENT_CHARS) {
            return new ToolResult(ok, body, false);
        }
        return new ToolResult(ok, body.substring(0, MAX_CONTENT_CHARS) + TRUNCATION_NOTICE, true);
    }
}
