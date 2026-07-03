package com.erp.assistant.application;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the assistant's tool catalogue (parsed once from {@code assistant/tools.json}) and converts it to the
 * Anthropic SDK's {@code Tool} shape, filtered by caller role.
 *
 * <p>The SDK's structured-outputs / JSON-schema helper module is deliberately excluded from the build (it
 * shares a split package with springdoc), so the {@code Tool.InputSchema} is assembled by hand from the
 * manifest's JSON Schema: each top-level property becomes an
 * {@code InputSchema.Properties} additional property, and {@code required} is copied verbatim.
 *
 * <p>Role filtering is a hard rule, applied uniformly by {@link #isVisible}: a caller with no granted
 * authorities (the read-only guest) is never offered a {@link ToolKind#WRITE} tool; and when a tool declares
 * {@code requiredRoles} in the manifest, the caller must hold at least one of them (as a {@code ROLE_*}
 * authority) regardless of read/write kind. The decision uses the Spring Security authorities on the
 * request's {@link Authentication}, never anything the model or client sends. This same check gates both
 * what is <em>advertised</em> to the model ({@link #toolsFor}) and, in {@code AgentLoopService}, what is
 * allowed to <em>execute</em> on a resumed write — a caller cannot bypass role filtering by forging a
 * tool_use the model was never offered.
 */
@Component
public class ToolRegistry {

    private final List<ToolSpec> specs;
    private final ObjectMapper mapper;

    public ToolRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
        this.specs = ToolManifest.load(mapper);
    }

    /** All tool specs (unfiltered), in manifest order. */
    public List<ToolSpec> specs() {
        return specs;
    }

    /** Looks up a tool by the name the model called; empty if unknown. */
    public java.util.Optional<ToolSpec> find(String name) {
        return specs.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /**
     * The SDK {@code Tool} list to advertise to the model for this caller, filtered by {@link #isVisible}.
     */
    public List<Tool> toolsFor(Authentication authentication) {
        List<Tool> tools = new ArrayList<>();
        for (ToolSpec spec : specs) {
            if (isVisible(spec, authentication)) {
                tools.add(toSdkTool(spec));
            }
        }
        return tools;
    }

    /**
     * Whether {@code authentication} may see/execute {@code spec}: a write tool requires at least one
     * granted role (never offered to the role-less guest); on top of that, if the manifest declares
     * {@code requiredRoles}, the caller must hold at least one of them. Used both to build the advertised
     * tool list and, by the agent loop, to re-check a resumed write before executing it.
     */
    public boolean isVisible(ToolSpec spec, Authentication authentication) {
        if (spec.isWrite() && !hasAnyRole(authentication)) {
            return false;
        }
        List<String> required = spec.requiredRoles();
        if (required == null || required.isEmpty()) {
            return true;
        }
        return hasAnyOfRoles(authentication, required);
    }

    /** True when the authenticated caller has at least one granted authority (i.e. is not the guest). */
    private static boolean hasAnyRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority() != null && authority.getAuthority().startsWith("ROLE_")) {
                return true;
            }
        }
        return false;
    }

    /** True when the caller holds a {@code ROLE_*} authority matching any of {@code roles} (bare, no prefix). */
    private static boolean hasAnyOfRoles(Authentication authentication, List<String> roles) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String granted = authority.getAuthority();
            if (granted == null) {
                continue;
            }
            for (String role : roles) {
                if (granted.equals("ROLE_" + role)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Builds the SDK {@code Tool}, assembling {@code InputSchema} by hand (no schema-helper module). */
    Tool toSdkTool(ToolSpec spec) {
        JsonNode schema = spec.inputSchema();
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        JsonNode propsNode = schema.get("properties");
        if (propsNode != null && propsNode.isObject()) {
            for (var entry : propsNode.properties()) {
                properties.putAdditionalProperty(entry.getKey(), JsonValue.from(toPlain(entry.getValue())));
            }
        }

        Tool.InputSchema.Builder inputSchema = Tool.InputSchema.builder().properties(properties.build());
        JsonNode requiredNode = schema.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            List<String> required = new ArrayList<>();
            requiredNode.forEach(r -> required.add(r.asText()));
            inputSchema.required(required);
        }

        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(inputSchema.build())
                .build();
    }

    /** Converts a Jackson node to plain Java (Map/List/scalars) so {@code JsonValue.from} carries it exactly. */
    private Object toPlain(JsonNode node) {
        return mapper.convertValue(node, Object.class);
    }
}
