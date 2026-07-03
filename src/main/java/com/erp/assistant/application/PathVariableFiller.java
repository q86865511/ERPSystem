package com.erp.assistant.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts a tool's {@code pathTemplate} {@code {var}} values from its input fields — the one piece of
 * {@code ToolManifest}'s input→HTTP mapping shared verbatim by both the production {@code RestToolInvoker}
 * (web layer) and the test {@code MockMvcToolInvoker}, so the two invokers cannot drift on validation
 * behaviour. Lives in {@code application} (not {@code web}) purely so the test invoker — which lives outside
 * the web module — can call it without a web-layer test dependency.
 *
 * <p>Deliberately does <em>not</em> percent-encode the values itself: {@code pathTemplate} keeps its
 * {@code {var}} placeholders verbatim, and the caller expands them through its own URI-template mechanism
 * ({@code UriComponentsBuilder}'s {@code build(Map)} for {@code RestToolInvoker}, {@code MockMvcRequestBuilders
 * .get(String, Object...)} for the test invoker) so each value is percent-encoded <em>exactly once</em>. Doing
 * the encoding here first and handing an already-encoded string to those same template expanders would encode
 * it a second time (a space would become {@code %2520} instead of {@code %20}, and a literal {@code /} would
 * become {@code %252F} instead of {@code %2F}).
 */
public final class PathVariableFiller {

    private PathVariableFiller() {}

    /**
     * Removes every {@code {var}} named in {@code template} from {@code fields}, returning them as a
     * name→value map for the caller to expand the template with. A missing/blank value or a dot-segment
     * ({@code .} or {@code ..}, which would navigate the path up/in-place once the template is expanded) is
     * rejected outright by throwing {@link IllegalArgumentException}, rather than silently filled with an
     * empty string or passed through. A raw {@code /} in a value is not rejected here — the URI-template
     * expansion step encodes it to {@code %2F}, a single path segment, not an extra one.
     */
    public static Map<String, String> extract(String template, ObjectNode fields) {
        Map<String, String> variables = new LinkedHashMap<>();
        int i = 0;
        while ((i = template.indexOf('{', i)) >= 0) {
            int end = template.indexOf('}', i);
            if (end < 0) {
                break;
            }
            String name = template.substring(i + 1, end);
            i = end + 1;

            JsonNode value = fields.remove(name);
            String text = value != null ? scalarText(value) : null;
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("path variable '" + name + "' is required and must not be blank");
            }
            if (".".equals(text) || "..".equals(text)) {
                throw new IllegalArgumentException("path variable '" + name + "' must not be a dot-segment");
            }
            variables.put(name, text);
        }
        return variables;
    }

    private static String scalarText(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }
}
