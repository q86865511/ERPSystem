package com.erp.assistant.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and parses the tool manifest ({@code assistant/tools.json} on the classpath) into {@link ToolSpec}s.
 * The manifest is the single source of truth for the assistant's tools (and, later, a standalone mcp-server),
 * so parsing lives here rather than being scattered across the registry and the invoker.
 *
 * <h3>Input → HTTP mapping</h3>
 * Each tool declares a {@code method} and a {@code pathTemplate}; {@link RestToolInvoker} maps a call's JSON
 * input onto an HTTP request with these rules:
 * <ul>
 *   <li><b>Path variables</b>: any {@code {var}} in {@code pathTemplate} is replaced by the input field
 *       {@code var} (URL-encoded). Those fields are consumed and do not appear again below.</li>
 *   <li><b>GET</b>: every remaining top-level input field becomes a query parameter
 *       ({@code ?field=value}); arrays/objects are not expected for GET tools.</li>
 *   <li><b>POST</b>: the remaining input object is sent as the JSON request body root (verbatim), so the
 *       tool's {@code input_schema} is authored to match the endpoint's request DTO.</li>
 * </ul>
 * None of the current tools use path variables, but the rule is defined so the manifest can grow without a
 * code change.
 */
public final class ToolManifest {

    private static final String MANIFEST_PATH = "assistant/tools.json";

    private ToolManifest() {}

    /** Loads the manifest from the classpath. Throws {@link IllegalStateException} if it is missing/invalid. */
    public static List<ToolSpec> load(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(MANIFEST_PATH).getInputStream()) {
            JsonNode root = mapper.readTree(in);
            return parse(root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read tool manifest " + MANIFEST_PATH, e);
        }
    }

    private static List<ToolSpec> parse(JsonNode root) {
        JsonNode toolsNode = root.get("tools");
        if (toolsNode == null || !toolsNode.isArray()) {
            throw new IllegalStateException("tool manifest must have a 'tools' array");
        }
        List<ToolSpec> specs = new ArrayList<>();
        for (JsonNode toolNode : toolsNode) {
            specs.add(parseTool(toolNode));
        }
        return List.copyOf(specs);
    }

    private static ToolSpec parseTool(JsonNode node) {
        String name = requireText(node, "name");
        ToolKind kind = ToolKind.fromManifest(requireText(node, "kind"));
        String method = requireText(node, "method").toUpperCase();
        String pathTemplate = requireText(node, "pathTemplate");
        String description = requireText(node, "description");
        JsonNode inputSchema = node.get("input_schema");
        if (inputSchema == null || !inputSchema.isObject()) {
            throw new IllegalStateException("tool '" + name + "' must have an object 'input_schema'");
        }
        List<String> requiredRoles = parseRequiredRoles(node.get("requiredRoles"));
        return new ToolSpec(name, kind, method, pathTemplate, description, inputSchema, requiredRoles);
    }

    /** Optional {@code requiredRoles} array; absent/empty means "no role restriction beyond read/write kind". */
    private static List<String> parseRequiredRoles(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        node.forEach(r -> roles.add(r.asText()));
        return List.copyOf(roles);
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("tool manifest entry missing text field '" + field + "'");
        }
        return value.asText();
    }
}
