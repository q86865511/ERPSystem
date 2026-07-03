package com.erp.assistant.api;

/**
 * Published when ERP Copilot actually executes a tool against the ERP API — every read, and every write
 * after the user confirmed it. The audit module listens for this and writes an append-only audit row.
 *
 * <p>Only summaries are carried, never full payloads: {@code inputSummary} and {@code resultSummary} are
 * short, truncated strings so the audit trail records <em>that</em> a tool ran and its shape, without
 * copying potentially large request/response bodies.
 *
 * @param actor         the authenticated username that drove the assistant
 * @param toolName      the tool that ran (e.g. {@code create_sales_order})
 * @param kind          {@code "read"} or {@code "write"}
 * @param inputSummary  a short, truncated summary of the tool input
 * @param ok            whether the invocation succeeded
 * @param resultSummary a short, truncated summary of the result
 */
public record AssistantToolExecutedEvent(
        String actor,
        String toolName,
        String kind,
        String inputSummary,
        boolean ok,
        String resultSummary) {
}
