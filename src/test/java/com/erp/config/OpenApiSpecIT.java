package com.erp.config;

import com.erp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the OpenAPI spec springdoc produces (via MockMvc — this environment can't bind a real
 * Tomcat loopback) and exports it to {@code target/openapi.json} for the frontend's TypeScript codegen.
 * Guards two regressions: nested-record DTO name collisions (springdoc would emit {@code Name_1}), and
 * BigDecimal leaking as a JSON {@code number} (it must be documented as {@code string}, matching the
 * runtime serialization in {@link JacksonConfig}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void specIsValidNoCollisionsMoneyIsString_andExported() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(body);

        // springdoc actually built a spec with schemas and paths.
        assertThat(root.path("openapi").asString()).startsWith("3.");
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(schemas.isObject()).isTrue();
        assertThat(schemas.propertyNames()).isNotEmpty();
        assertThat(root.path("paths").isObject()).isTrue();

        // No nested-record DTO name collisions (springdoc disambiguates clashes as Name_1, Name_2, ...).
        List<String> collided = schemas.propertyNames().stream()
                .filter(n -> n.matches(".*_\\d+$")).toList();
        assertThat(collided).as("schema name collisions — add @Schema(name=...)").isEmpty();

        // BigDecimal documented as string (no JSON number anywhere), matching runtime serialization.
        assertThat(body.replaceAll("\\s", "")).doesNotContain("\"type\":\"number\"");

        // No merged operations: two handler methods on one path+method make springdoc emit a `oneOf`
        // response, which the typed client can't use cleanly. Give each operation a distinct path.
        assertThat(body).as("merged path operations — give each its own path").doesNotContain("oneOf");

        // Guard the nested-record name clash that the _1 check can't see: two distinct Java records with
        // the same simple name (e.g. TrialBalance.Line vs JournalEntryRequest.Line) silently collapse to
        // one schema, mistyping one of them. The trial-balance row must carry accounting columns.
        String tbRef = schemas.path("TrialBalance").path("properties").path("lines").path("items")
                .path("$ref").asString();
        String tbRowSchema = tbRef.substring(tbRef.lastIndexOf('/') + 1);
        assertThat(schemas.path(tbRowSchema).path("properties").propertyNames())
                .as("trial-balance row columns").contains("code", "accountClass");

        // Export for `npm run gen:api` (the running-server route is unavailable in this sandbox).
        Path out = Path.of("target", "openapi.json");
        Files.writeString(out, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        assertThat(Files.size(out)).isPositive();
    }
}
