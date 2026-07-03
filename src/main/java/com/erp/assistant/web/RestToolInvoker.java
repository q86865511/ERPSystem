package com.erp.assistant.web;

import com.erp.assistant.application.AssistantProperties;
import com.erp.assistant.application.PathVariableFiller;
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

        try {
            // The template keeps its {var} placeholders verbatim; pathVariables holds the raw (unencoded)
            // values PathVariableFiller pulled (and validated) out of the input. Expanding them via
            // UriBuilder.build(Map) below — rather than substituting a pre-encoded string ourselves — is what
            // makes the encoding happen exactly once (see PathVariableFiller's class doc).
            Map<String, String> pathVariables = PathVariableFiller.extract(spec.pathTemplate(), remaining);
            if ("GET".equals(spec.method())) {
                return get(spec.pathTemplate(), pathVariables, remaining, bearerToken);
            }
            return post(spec.pathTemplate(), pathVariables, remaining, bearerToken);
        } catch (RuntimeException ex) {
            // Transport/serialization failure (not an HTTP error status, which we capture below), or a path
            // variable that failed validation in PathVariableFiller: report it as an error result rather than
            // aborting the whole turn.
            return ToolResult.error("{\"error\":\"tool invocation failed\",\"detail\":\""
                    + safe(ex.getMessage()) + "\"}");
        }
    }

    private ToolResult get(String pathTemplate, Map<String, String> pathVariables, ObjectNode fields,
                           String bearerToken) {
        // Build the URI through RestClient's own UriBuilder (starts from the configured base URL, if any)
        // rather than pre-encoding a String ourselves — passing an already-encoded String to .uri(String)
        // would have it percent-encoded a second time (e.g. a space would become %2520 instead of %20).
        // UriBuilder.queryParam(name, value) and the path-template variables in .build(Map) each encode
        // exactly once, so a raw "&", "/", space or non-ASCII value cannot inject an extra query param/path
        // segment or otherwise corrupt the request line.
        RestClient.ResponseSpec response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(pathTemplate);
                    for (Map.Entry<String, JsonNode> entry : fields.properties()) {
                        uriBuilder.queryParam(entry.getKey(), scalarText(entry.getValue()));
                    }
                    return uriBuilder.build(pathVariables);
                })
                .headers(h -> auth(h, bearerToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { /* capture, never throw */ });
        return toResult(response);
    }

    private ToolResult post(String pathTemplate, Map<String, String> pathVariables, ObjectNode body,
                            String bearerToken) {
        RestClient.ResponseSpec response = restClient.post()
                .uri(uriBuilder -> uriBuilder.path(pathTemplate).build(pathVariables))
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

    private static String scalarText(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static String safe(String message) {
        return message == null ? "" : message.replace("\"", "'");
    }
}
