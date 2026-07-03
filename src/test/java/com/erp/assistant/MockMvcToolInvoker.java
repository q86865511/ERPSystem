package com.erp.assistant;

import com.erp.assistant.application.PathVariableFiller;
import com.erp.assistant.application.ToolInvoker;
import com.erp.assistant.application.ToolResult;
import com.erp.assistant.application.ToolSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A {@link ToolInvoker} that drives the ERP's real controllers through {@link MockMvc} instead of a socket
 * (this box's sandbox blocks loopback HTTP). It applies the same input→HTTP mapping as the production
 * {@code RestToolInvoker} — path variables (via the shared {@link PathVariableFiller}), GET query params,
 * POST body root — and forwards the caller's bearer token so the real security filter chain authorizes the
 * call. HTTP error statuses become {@code ok=false} results, exactly as in production.
 */
public class MockMvcToolInvoker implements ToolInvoker {

    private final MockMvc mvc;
    private final ObjectMapper mapper;

    public MockMvcToolInvoker(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    @Override
    public ToolResult invoke(ToolSpec spec, JsonNode input, String bearerToken) {
        ObjectNode remaining = input != null && input.isObject()
                ? ((ObjectNode) input).deepCopy()
                : mapper.createObjectNode();
        try {
            // Expand the {var} template through UriComponentsBuilder (single-pass encoding — see
            // PathVariableFiller's class doc) rather than substituting a pre-encoded string into the path
            // ourselves, which would double-encode it. Building a java.net.URI (not a String re-fed through
            // MockMvcRequestBuilders' own template parsing) keeps this a single encoding pass end to end.
            Map<String, String> pathVariables = PathVariableFiller.extract(spec.pathTemplate(), remaining);
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(spec.pathTemplate());
            if ("GET".equals(spec.method()) && !remaining.isEmpty()) {
                uriBuilder.query(queryString(remaining));
            }
            URI uri = uriBuilder.build(pathVariables);
            MockHttpServletRequestBuilder builder = "GET".equals(spec.method())
                    ? get(uri)
                    : post(uri).contentType(MediaType.APPLICATION_JSON).content(remaining.toString());
            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            builder.accept(MediaType.APPLICATION_JSON);

            MvcResult result = mvc.perform(builder).andReturn();
            String body = result.getResponse().getContentAsString();
            int status = result.getResponse().getStatus();
            return status >= 200 && status < 300 ? ToolResult.ok(body)
                    : ToolResult.error(body.isBlank() ? "{\"status\":" + status + "}" : body);
        } catch (Exception e) {
            return ToolResult.error("{\"error\":\"tool invocation failed\"}");
        }
    }

    /** The raw {@code a=1&b=2} query content (no leading {@code ?}) for {@code fields}, or "" if none. */
    private String queryString(ObjectNode fields) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : fields.properties()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(scalar(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private static String scalar(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }
}
