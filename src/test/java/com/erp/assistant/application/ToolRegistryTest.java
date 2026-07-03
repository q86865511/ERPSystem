package com.erp.assistant.application;

import com.anthropic.models.messages.Tool;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolRegistry}: the manifest loads and every tool converts to an SDK {@code Tool}
 * with its schema preserved; write tools are filtered out for a role-less (guest) caller but kept for a
 * caller holding a role.
 */
class ToolRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ToolRegistry registry = new ToolRegistry(MAPPER);

    @Test
    void loadsAllTwelveManifestToolsWithExpectedNamesAndKinds() {
        List<ToolSpec> specs = registry.specs();
        assertThat(specs).extracting(ToolSpec::name).containsExactlyInAnyOrder(
                "search_items", "search_partners", "get_inventory_status", "get_ar_aging",
                "get_kpi_summary", "get_reconciliation_health",
                "get_income_statement", "get_general_ledger", "get_revenue_trend", "get_cash_flow",
                "get_budget_variance", "create_sales_order");
        // Exactly one write tool (create_sales_order); the rest are reads.
        assertThat(specs).filteredOn(ToolSpec::isWrite).extracting(ToolSpec::name)
                .containsExactly("create_sales_order");
    }

    @Test
    void everyManifestToolConvertsToAnSdkTool() {
        for (ToolSpec spec : registry.specs()) {
            Tool tool = registry.toSdkTool(spec);
            assertThat(tool.name()).isEqualTo(spec.name());
            assertThat(tool.description()).contains(spec.description());
            // The input schema is present (assembled by hand from the manifest JSON Schema).
            assertThat(tool.inputSchema()).isNotNull();
        }
    }

    @Test
    void schemaPropertiesAndRequiredAreFaithful() {
        ToolSpec createSo = registry.find("create_sales_order").orElseThrow();
        Tool tool = registry.toSdkTool(createSo);
        var properties = tool.inputSchema().properties().orElseThrow();
        // The three top-level properties survive the manual conversion.
        assertThat(properties._additionalProperties().keySet())
                .containsExactlyInAnyOrder("partnerId", "orderDate", "lines");
        // required is copied verbatim.
        assertThat(tool.inputSchema().required().orElseThrow())
                .containsExactlyInAnyOrder("partnerId", "orderDate", "lines");
    }

    @Test
    void guestDoesNotSeeWriteTools() {
        // Guest = authenticated but no ROLE_* authority.
        Authentication guest = new UsernamePasswordAuthenticationToken("guest", null, List.of());
        List<Tool> tools = registry.toolsFor(guest);
        assertThat(tools).extracting(Tool::name).doesNotContain("create_sales_order");
        // All read tools are still offered.
        assertThat(tools).hasSize(registry.specs().size() - 1);
    }

    @Test
    void roleHolderSeesWriteTools() {
        Authentication sales = new UsernamePasswordAuthenticationToken("alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_SALES")));
        List<Tool> tools = registry.toolsFor(sales);
        assertThat(tools).extracting(Tool::name).contains("create_sales_order");
        assertThat(tools).hasSize(registry.specs().size());
    }

    @Test
    void nullAuthenticationIsTreatedAsGuest() {
        List<Tool> tools = registry.toolsFor(null);
        assertThat(tools).extracting(Tool::name).doesNotContain("create_sales_order");
    }
}
