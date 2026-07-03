package com.erp.assistant;

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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A {@link ToolInvoker} that drives the ERP's real controllers through {@link MockMvc} instead of a socket
 * (this box's sandbox blocks loopback HTTP). It applies the same input→HTTP mapping as the production
 * {@code RestToolInvoker} — path variables, GET query params, POST body root — and forwards the caller's
 * bearer token so the real security filter chain authorizes the call. HTTP error statuses become
 * {@code ok=false} results, exactly as in production.
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
        String path = fillPathVariables(spec.pathTemplate(), remaining);
        try {
            MockHttpServletRequestBuilder builder = "GET".equals(spec.method())
                    ? get(path + queryString(remaining))
                    : post(path).contentType(MediaType.APPLICATION_JSON).content(remaining.toString());
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

    private String queryString(ObjectNode fields) {
        if (fields.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
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

    private String fillPathVariables(String template, ObjectNode fields) {
        String path = template;
        int i = 0;
        while ((i = path.indexOf('{', i)) >= 0) {
            int end = path.indexOf('}', i);
            if (end < 0) {
                break;
            }
            String name = path.substring(i + 1, end);
            JsonNode value = fields.remove(name);
            path = path.replace("{" + name + "}", value != null ? scalar(value) : "");
            i = 0;
        }
        return path;
    }

    private static String scalar(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }
}
