package com.erp.assistant.web;

import com.erp.assistant.application.ToolKind;
import com.erp.assistant.application.ToolSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RestToolInvoker}'s GET query-parameter construction, in particular that query values
 * are URL-encoded ({@code UriComponentsBuilder.encode()}) rather than concatenated raw — a value containing
 * {@code &}, whitespace or non-ASCII text must not corrupt the query string or inject an extra parameter.
 * Also covers path-variable expansion: {@code PathVariableFiller} extracts/validates the raw values, and
 * {@code RestToolInvoker} expands them into the {@code {var}} template via {@code UriBuilder.build(Map)} — a
 * single encoding pass, so a value containing a space, non-ASCII text or even a raw {@code /} lands as
 * percent-encoded content of one path segment (never split into an extra segment), while a blank value or a
 * dot-segment ({@code ..}) is rejected outright. A request interceptor captures the outgoing {@link URI}
 * without any real network call.
 */
class RestToolInvokerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ToolSpec GET_SPEC = new ToolSpec(
            "search_partners", ToolKind.READ, "GET", "/api/masterdata/partners",
            "test tool", MAPPER.createObjectNode(), List.of());

    private static final ToolSpec GL_SPEC = new ToolSpec(
            "get_general_ledger", ToolKind.READ, "GET", "/api/reporting/general-ledger/{accountCode}",
            "test tool", MAPPER.createObjectNode(), List.of());

    @Test
    void queryValueContainingAmpersandIsEncodedNotConcatenatedRaw() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"name\":\"Acme & Co\"}");
        invoker.invoke(GET_SPEC, input, null);

        URI uri = interceptor.capturedUri;
        assertThat(uri).isNotNull();
        // The raw "&" must be percent-encoded (%26), not left as a literal query-param separator.
        assertThat(uri.getRawQuery()).contains("name=Acme%20%26%20Co");
        assertThat(uri.getRawQuery()).doesNotContain("Acme & Co");
    }

    @Test
    void queryValueContainingSpaceIsEncoded() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"name\":\"widget one\"}");
        invoker.invoke(GET_SPEC, input, null);

        assertThat(interceptor.capturedUri.getRawQuery()).contains("name=widget%20one");
    }

    @Test
    void queryValueContainingNonAsciiIsPercentEncoded() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"name\":\"客戶\"}");
        invoker.invoke(GET_SPEC, input, null);

        String rawQuery = interceptor.capturedUri.getRawQuery();
        assertThat(rawQuery).doesNotContain("客戶");
        // UTF-8 percent-encoding of 客戶.
        assertThat(rawQuery).contains("%E5%AE%A2%E6%88%B6");
    }

    @Test
    void pathVariableContainingSpaceIsEncodedIntoASinglePathSegment() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"accountCode\":\"2100 GRIR\"}");
        invoker.invoke(GL_SPEC, input, null);

        URI uri = interceptor.capturedUri;
        assertThat(uri).isNotNull();
        assertThat(uri.getRawPath()).contains("/api/reporting/general-ledger/2100%20GRIR");
    }

    @Test
    void pathVariableContainingNonAsciiIsEncodedIntoASinglePathSegment() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"accountCode\":\"客戶\"}");
        invoker.invoke(GL_SPEC, input, null);

        URI uri = interceptor.capturedUri;
        assertThat(uri).isNotNull();
        assertThat(uri.getRawPath()).doesNotContain("客戶");
        // UTF-8 percent-encoding of 客戶, still a single path segment (no extra "/").
        assertThat(uri.getRawPath()).contains("/api/reporting/general-ledger/%E5%AE%A2%E6%88%B6");
    }

    @Test
    void pathVariableContainingSlashIsEncodedNotLeftAsAnExtraSegment() {
        // A raw "/" is percent-encoded (%2F) as part of the single path segment, exactly like the query-param
        // case above — not rejected, since encoding already neutralises any path-traversal/extra-segment risk.
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"accountCode\":\"2100/../../etc\"}");
        var result = invoker.invoke(GL_SPEC, input, null);

        assertThat(result.ok()).isTrue();
        URI uri = interceptor.capturedUri;
        assertThat(uri).isNotNull();
        assertThat(uri.getRawPath()).isEqualTo("/api/reporting/general-ledger/2100%2F..%2F..%2Fetc");
    }

    @Test
    void pathVariableThatIsADotSegmentIsRejected() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"accountCode\":\"..\"}");
        var result = invoker.invoke(GL_SPEC, input, null);

        assertThat(result.ok()).isFalse();
        assertThat(interceptor.capturedUri).isNull();
    }

    @Test
    void pathVariableThatIsBlankIsRejected() {
        CapturingInterceptor interceptor = new CapturingInterceptor();
        RestToolInvoker invoker = invoker(interceptor);

        JsonNode input = MAPPER.readTree("{\"accountCode\":\"   \"}");
        var result = invoker.invoke(GL_SPEC, input, null);

        assertThat(result.ok()).isFalse();
        assertThat(interceptor.capturedUri).isNull();
    }

    private static RestToolInvoker invoker(CapturingInterceptor interceptor) {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost")
                .requestInterceptor(interceptor)
                .build();
        return new RestToolInvoker(client, MAPPER);
    }

    /** Captures the request URI and short-circuits with a canned 200 response — no real network call. */
    private static final class CapturingInterceptor implements org.springframework.http.client.ClientHttpRequestInterceptor {
        URI capturedUri;

        @Override
        public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                            org.springframework.http.client.ClientHttpRequestExecution execution)
                throws java.io.IOException {
            capturedUri = request.getURI();
            MockClientHttpResponse response = new MockClientHttpResponse(
                    "{}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return response;
        }
    }
}
