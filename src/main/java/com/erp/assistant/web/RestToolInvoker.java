package com.erp.assistant.web;

import com.erp.assistant.application.AssistantProperties;
import com.erp.assistant.application.ToolInvoker;
import com.erp.assistant.application.ToolResult;
import com.erp.assistant.application.ToolSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link ToolInvoker} that calls back into this ERP's own REST API over HTTP, using Spring's
 * {@link RestClient} against {@link AssistantProperties#selfBaseUrl()}. The caller's bearer token is
 * forwarded so the ERP applies the same authorization it would for a direct request.
 *
 * <p>Lives in the {@code web} layer (not {@code application}) because it is an HTTP client — the module's
 * outbound web surface. The agent loop depends only on the {@link ToolInvoker} interface in
 * {@code application}; Spring wires this implementation in.
 *
 * <p>Mapping follows {@code ToolManifest}'s contract: {@code {var}} path placeholders are filled from the
 * input (URL-encoded), GET tools turn the remaining input fields into query parameters, and POST tools send
 * the remaining input object as the JSON body root. HTTP error statuses are captured (not thrown) and
 * returned as {@code ok=false} results carrying the response body, so the model can recover.
 */
@Component
public class RestToolInvoker implements ToolInvoker {

    private final RestClient restClient;
    private final ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    public RestToolInvoker(AssistantProperties properties, ObjectMapper mapper) {
        this.mapper = mapper;
        this.restClient = RestClient.builder().baseUrl(properties.selfBaseUrl()).build();
    }

    /** Test seam: inject a RestClient bound to a MockMvc-backed request factory. */
    RestToolInvoker(RestClient restClient, ObjectMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    @Override
    public ToolResult invoke(ToolSpec spec, JsonNode input, String bearerToken) {
        ObjectNode remaining = input != null && input.isObject()
                ? ((ObjectNode) input).deepCopy()
                : mapper.createObjectNode();

        String path = fillPathVariables(spec.pathTemplate(), remaining);

        try {
            if ("GET".equals(spec.method())) {
                return get(path, remaining, bearerToken);
            }
            return post(path, remaining, bearerToken);
        } catch (RuntimeException ex) {
            // Transport/serialization failure (not an HTTP error status, which we capture below): report it
            // as an error result rather than aborting the whole turn.
            return ToolResult.error("{\"error\":\"tool invocation failed\",\"detail\":\""
                    + safe(ex.getMessage()) + "\"}");
        }
    }

    private ToolResult get(String path, ObjectNode fields, String bearerToken) {
        // Build the URI through RestClient's own UriBuilder (starts from the configured base URL, if any)
        // rather than pre-encoding a String ourselves — passing an already-encoded String to .uri(String)
        // would have it percent-encoded a second time (e.g. a space would become %2520 instead of %20).
        // UriBuilder.queryParam(name, value) encodes each value exactly once at .build(), so a raw "&",
        // space or non-ASCII value cannot inject an extra query param or otherwise corrupt the request line.
        RestClient.ResponseSpec response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    for (Map.Entry<String, JsonNode> entry : fields.properties()) {
                        uriBuilder.queryParam(entry.getKey(), scalarText(entry.getValue()));
                    }
                    return uriBuilder.build();
                })
                .headers(h -> auth(h, bearerToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { /* capture, never throw */ });
        return toResult(response);
    }

    private ToolResult post(String path, ObjectNode body, String bearerToken) {
        RestClient.ResponseSpec response = restClient.post()
                .uri(path)
                .headers(h -> auth(h, bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { /* capture, never throw */ });
        return toResult(response);
    }

    /** Reads the response entity and packages it: 2xx ⇒ ok, otherwise error, body carried through either way. */
    private ToolResult toResult(RestClient.ResponseSpec response) {
        var entity = response.toEntity(String.class);
        String body = entity.getBody() != null ? entity.getBody() : "";
        if (entity.getStatusCode().is2xxSuccessful()) {
            return ToolResult.ok(body);
        }
        String detail = body.isBlank()
                ? "{\"error\":\"request failed\",\"status\":" + entity.getStatusCode().value() + "}"
                : body;
        return ToolResult.error(detail);
    }

    private static void auth(HttpHeaders headers, String bearerToken) {
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.setBearerAuth(bearerToken);
        }
    }

    /**
     * Replaces every {@code {var}} in the template with the (URL-encoded) input field {@code var}, removing
     * that field from {@code fields} so it is not also sent as a query param / in the body.
     */
    private String fillPathVariables(String template, ObjectNode fields) {
        String path = template;
        List<String> names = new ArrayList<>();
        int i = 0;
        while ((i = path.indexOf('{', i)) >= 0) {
            int end = path.indexOf('}', i);
            if (end < 0) {
                break;
            }
            names.add(path.substring(i + 1, end));
            i = end + 1;
        }
        for (String name : names) {
            JsonNode value = fields.remove(name);
            String encoded = value != null
                    ? UriComponentsBuilder.fromPath("/").pathSegment(scalarText(value)).build().getPathSegments().get(0)
                    : "";
            path = path.replace("{" + name + "}", encoded);
        }
        return path;
    }

    private static String scalarText(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static String safe(String message) {
        return message == null ? "" : message.replace("\"", "'");
    }
}
