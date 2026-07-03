package com.erp.assistant;

import com.erp.TestcontainersConfiguration;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.ToolInvoker;
import com.erp.audit.application.AuditLogRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static com.erp.iam.JwtTestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration for the assistant's tool loop over the real security filter chain and a real DB
 * (Testcontainers), with the model replaced by a {@link ScriptedAnthropicPort} and tools driven through a
 * {@link MockMvcToolInvoker} (no socket — the sandbox blocks loopback). The assistant is enabled
 * ({@code app.assistant.enabled=true}) but the scripted port bean wins over the SDK adapter (which is
 * {@code @ConditionalOnMissingBean}), so no {@code ANTHROPIC_API_KEY} is needed.
 *
 * <p>Covers: (a) a read tool call surfacing seed data; (b) the full human-in-the-loop write flow — a
 * confirmation pause with no DB change, then an approved decision that creates the DRAFT sales order, audits
 * it, and leaves the books balanced; (c) a declined write leaving no order; (d) guest never sees the write
 * tool; (e) the per-user rate limit → 429.
 */
@Import({TestcontainersConfiguration.class, AssistantToolLoopIT.ScriptedConfig.class})
@SpringBootTest(properties = {
        "app.assistant.enabled=true",
        // Small caps so the rate-limit scenario is cheap to trigger.
        "app.assistant.rate-limit.max-chats-per-hour=2",
        "app.assistant.rate-limit.max-concurrent-streams=1"
})
@AutoConfigureMockMvc
class AssistantToolLoopIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder jwt;
    @Autowired
    private MasterDataService masterData;
    @Autowired
    private StockAdjustmentService stockAdjustments;
    @Autowired
    private WarehouseRepository warehouses;
    @Autowired
    private LocationRepository locations;
    @Autowired
    private AuditLogRepository auditLog;
    @Autowired
    private ScriptedAnthropicPort scriptedPort;

    @BeforeEach
    void resetScript() {
        scriptedPort.reset();
    }

    /**
     * The scripted port and a MockMvc-backed tool invoker. The invoker runs the ERP's real controllers
     * through the same MockMvc, forwarding the caller's bearer token. Marked {@code @Primary} so it is the
     * {@link ToolInvoker} the agent loop uses (over the production RestToolInvoker).
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedConfig {
        @Bean
        AnthropicPort scriptedAnthropicPort() {
            return new ScriptedAnthropicPort();
        }

        @Bean
        @Primary
        ToolInvoker mockMvcToolInvoker(MockMvc mvc, ObjectMapper mapper) {
            return new MockMvcToolInvoker(mvc, mapper);
        }
    }

    // ---- (a) read tool surfaces seed data -------------------------------------------------------

    @Test
    void readToolReturnsSeededInventoryData() throws Exception {
        // Seed a distinctive item and give it on-hand stock so items-status has a row we can recognise.
        String sku = "COPILOT-READ-1";
        Long itemId = masterData.createItem(sku, "Copilot read item", ItemType.RAW, "EA", true,
                new BigDecimal("7"), null, null).getId();
        stockAdjustments.postAdjustment(itemId, stockLocationId(), new BigDecimal("15"),
                new BigDecimal("7"), "seed", java.time.LocalDate.of(2026, 6, 15), "tester");

        // Turn 1: model calls get_inventory_status. Turn 2: model answers (loop then ends).
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_read", "get_inventory_status", "{}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Here is your inventory.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 20L, 3L));
        });

        String body = chat("alice", "SALES",
                "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"stock?\"}]}]}");

        // The tool_result frame carries the seeded item's sku (proof the read hit the real DB).
        assertThat(body).contains("event:tool_call");
        assertThat(body).contains("get_inventory_status");
        assertThat(body).contains("event:tool_result");
        assertThat(body).contains(sku);
        assertThat(body).contains("event:done");
    }

    // ---- (b) full human-in-the-loop write flow --------------------------------------------------

    @Test
    void writeToolPausesThenCreatesOrderOnApprovalAndKeepsBooksBalanced() throws Exception {
        Long partnerId = masterData.createPartner("COPILOT-CUST-1", "Copilot customer", false, true, null,
                30, null, null).getId();
        Long itemId = masterData.createItem("COPILOT-SO-1", "Copilot SO item", ItemType.FINISHED, "EA", true,
                new BigDecimal("5"), null, null).getId();
        String input = "{\"partnerId\":" + partnerId + ",\"orderDate\":\"2026-07-03\",\"lines\":["
                + "{\"itemId\":" + itemId + ",\"qtyOrdered\":3,\"unitPrice\":9}]}";

        long ordersBefore = countOrders("bob");

        // First chat: the model asks to create the sales order (write) → awaiting_confirmation, no DB change.
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("I'll create that draft order.");
            turn.onToolUse("tu_write", "create_sales_order", input);
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 10L, 5L));
        });
        String first = chat("bob", "SALES",
                "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"make a draft order\"}]}]}");

        assertThat(first).contains("event:awaiting_confirmation");
        assertThat(first).contains("\"stopReason\":\"awaiting_confirmation\"");
        assertThat(countOrders("bob")).isEqualTo(ordersBefore);   // nothing created yet

        // Resume with an approve decision + the full history (assistant tool_use replayed). After the write
        // result is fed back, the model narrates success and ends.
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Created the draft sales order.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 30L, 6L));
        });
        String resumeBody = "{\"messages\":["
                + "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"make a draft order\"}]},"
                + "{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"I'll create that draft order.\"},"
                + "{\"type\":\"tool_use\",\"id\":\"tu_write\",\"name\":\"create_sales_order\",\"input\":" + input + "}]}"
                + "],\"decision\":{\"toolUseId\":\"tu_write\",\"approved\":true}}";
        String second = chat("bob", "SALES", resumeBody);

        assertThat(second).contains("event:tool_call");
        assertThat(second).contains("create_sales_order");
        assertThat(second).contains("event:tool_result");
        assertThat(second).contains("event:done");

        // The DRAFT sales order landed in the DB.
        assertThat(countOrders("bob")).isEqualTo(ordersBefore + 1);

        // The audit trail has an ASSISTANT_TOOL_EXECUTED row for the write.
        assertThat(auditLog.findAll()).anyMatch(a ->
                "ASSISTANT_TOOL_EXECUTED".equals(a.getEventType())
                        && "create_sales_order".equals(a.getRefId())
                        && "bob".equals(a.getActor()));

        // Signature invariant: a DRAFT SO posts no journals, so the books stay balanced.
        mvc.perform(get("/api/reporting/reconciliation").header("Authorization", bearer(jwt, "bob", "SALES")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"trialBalanceBalanced\":true"));
    }

    // ---- (b2) replay: a decision for an already-answered tool_use is refused ----------------------

    @Test
    void replayingAnAlreadyAnsweredApprovedDecisionDoesNotCreateASecondOrder() throws Exception {
        Long partnerId = masterData.createPartner("COPILOT-CUST-REPLAY", "Copilot replay customer", false, true,
                null, 30, null, null).getId();
        Long itemId = masterData.createItem("COPILOT-SO-REPLAY", "Copilot replay SO item", ItemType.FINISHED,
                "EA", true, new BigDecimal("5"), null, null).getId();
        String input = "{\"partnerId\":" + partnerId + ",\"orderDate\":\"2026-07-03\",\"lines\":["
                + "{\"itemId\":" + itemId + ",\"qtyOrdered\":2,\"unitPrice\":11}]}";

        long ordersBefore = countOrders("erin");

        // First resume: approve the write. It executes once and the model narrates success.
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Created the draft sales order.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 30L, 6L));
        });
        String firstResumeBody = "{\"messages\":["
                + "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"make a draft order\"}]},"
                + "{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"tu_replay\",\"name\":\"create_sales_order\",\"input\":" + input + "}]}"
                + "],\"decision\":{\"toolUseId\":\"tu_replay\",\"approved\":true}}";
        String first = chat("erin", "SALES", firstResumeBody);

        assertThat(first).contains("event:tool_call");
        assertThat(first).contains("event:done");
        assertThat(countOrders("erin")).isEqualTo(ordersBefore + 1);   // exactly one order created

        // Replay: the client resends the same decision, now with the tool_result from the first resume
        // already present in the history (as a real client would echo it back). The service must refuse —
        // no second model turn is even scripted, so if it were (mis)executed the test would fail on an
        // empty scripted-port queue rather than silently passing.
        String replayBody = "{\"messages\":["
                + "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"make a draft order\"}]},"
                + "{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"tu_replay\",\"name\":\"create_sales_order\",\"input\":" + input + "}]},"
                + "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"toolUseId\":\"tu_replay\","
                + "\"content\":\"{\\\"id\\\":1,\\\"status\\\":\\\"DRAFT\\\"}\",\"isError\":false}]}"
                + "],\"decision\":{\"toolUseId\":\"tu_replay\",\"approved\":true}}";
        String replay = chat("erin", "SALES", replayBody);

        assertThat(replay).contains("event:error");
        assertThat(replay).doesNotContain("event:tool_call");   // the write did not run a second time
        assertThat(countOrders("erin")).isEqualTo(ordersBefore + 1);   // DB order count unchanged
    }

    // ---- (c) declined write leaves no order -----------------------------------------------------

    @Test
    void declinedWriteCreatesNoOrder() throws Exception {
        Long partnerId = masterData.createPartner("COPILOT-CUST-2", "Copilot customer 2", false, true, null,
                30, null, null).getId();
        Long itemId = masterData.createItem("COPILOT-SO-2", "Copilot SO item 2", ItemType.FINISHED, "EA",
                true, new BigDecimal("5"), null, null).getId();
        String input = "{\"partnerId\":" + partnerId + ",\"orderDate\":\"2026-07-03\",\"lines\":["
                + "{\"itemId\":" + itemId + ",\"qtyOrdered\":1,\"unitPrice\":9}]}";

        long ordersBefore = countOrders("carol");

        // The model narrates the decline (no tool execution on the resume).
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Understood, I won't create it.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 12L, 3L));
        });
        String resumeBody = "{\"messages\":["
                + "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"make a draft order\"}]},"
                + "{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"tu_dec\",\"name\":\"create_sales_order\",\"input\":" + input + "}]}"
                + "],\"decision\":{\"toolUseId\":\"tu_dec\",\"approved\":false}}";
        String body = chat("carol", "SALES", resumeBody);

        assertThat(body).contains("event:done");
        assertThat(body).doesNotContain("event:tool_call");   // the write never ran
        assertThat(countOrders("carol")).isEqualTo(ordersBefore);
    }

    // ---- (d) guest never sees the write tool ----------------------------------------------------

    @Test
    void guestChatWorksAndGuestCannotWriteDirectly() throws Exception {
        // A role-less guest can still chat (reads only). The write-tool filtering itself is proven at the
        // unit level in ToolRegistryTest; here we confirm the guest chat streams normally, and that even if
        // a write were attempted it is blocked by the role gate (so offering it would be pointless).
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Hi.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 3L, 1L));
        });
        String body = chat("guest", null,
                "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}");
        assertThat(body).contains("event:done");

        mvc.perform(post("/api/sales/sales-orders").header("Authorization", bearer(jwt, "guest"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerId\":1,\"orderDate\":\"2026-07-03\",\"lines\":[]}"))
                .andExpect(status().isForbidden());
    }

    // ---- (e) rate limit → 429 -------------------------------------------------------------------

    @Test
    void exceedingHourlyChatCapReturns429() throws Exception {
        // Cap is 2/hour (test properties). Two chats succeed; the third is rate-limited.
        for (int i = 0; i < 2; i++) {
            scriptedPort.enqueue(turn -> {
                turn.onTextDelta("ok");
                turn.onEnd(new AnthropicPort.StopInfo("end_turn", 1L, 1L));
            });
            chat("dave", "SALES",
                    "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}");
        }
        mvc.perform(post("/api/assistant/chat").header("Authorization", bearer(jwt, "dave", "SALES"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** Performs a chat POST and drives the SSE async dispatch, returning the full streamed body. */
    private String chat(String user, String role, String body) throws Exception {
        String token = role == null ? bearer(jwt, user) : bearer(jwt, user, role);
        MvcResult started = mvc.perform(post("/api/assistant/chat")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request -> assertThat(request.getRequest().isAsyncStarted()).isTrue())
                .andReturn();
        // The loop runs on the assistant SSE executor (a real thread pool). Block until the emitter completes
        // and its async result is available, then dispatch to render the buffered SSE frames.
        started.getAsyncResult(5000);
        return mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();
    }

    /** The main warehouse's STOCK location, where seed stock adjustments are posted. */
    private Long stockLocationId() {
        Long warehouseId = warehouses.findByCode("WH1").orElseThrow().getId();
        return locations.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK).orElseThrow().getId();
    }

    /** Counts sales orders visible to a caller (via the list endpoint), for before/after comparison. */
    private long countOrders(String user) throws Exception {
        String json = mvc.perform(get("/api/sales/sales-orders").header("Authorization", bearer(jwt, user, "SALES")))
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(json).size();
    }
}
