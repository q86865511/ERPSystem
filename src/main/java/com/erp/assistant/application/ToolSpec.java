package com.erp.assistant.application;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * One tool as loaded from the manifest ({@code assistant/tools.json}). This is the semantic description the
 * rest of the module works with; {@link ToolRegistry} turns it into the SDK's {@code Tool} shape, and
 * {@link ToolInvoker} turns a call into an HTTP request against the ERP's own API.
 *
 * @param name         tool name the model calls (matches the SDK {@code Tool.name})
 * @param kind         read or write (write ⇒ human-in-the-loop confirmation, hidden from guests)
 * @param method       HTTP method used to invoke it ({@code GET} or {@code POST})
 * @param pathTemplate URL path, possibly with {@code {var}} placeholders filled from the tool input
 * @param description  the model-facing description
 * @param inputSchema  the JSON Schema for the tool's input (an {@code object} schema)
 * @param requiredRoles the {@code ROLE_*} suffixes (without prefix) a caller must hold at least one of to
 *                      see/execute this tool; empty means any role-holding (non-guest) caller for a write
 *                      tool, or anyone authenticated for a read tool (see {@link ToolRegistry#toolsFor})
 */
public record ToolSpec(
        String name,
        ToolKind kind,
        String method,
        String pathTemplate,
        String description,
        JsonNode inputSchema,
        List<String> requiredRoles) {

    public boolean isWrite() {
        return kind == ToolKind.WRITE;
    }
}
