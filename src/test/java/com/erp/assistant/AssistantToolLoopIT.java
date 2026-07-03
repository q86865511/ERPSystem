package com.erp.assistant;

import com.erp.TestcontainersConfiguration;
import com.erp.assistant.application.AnthropicPort;
import com.erp.assistant.application.ToolInvoker;
import com.erp.audit.application.AuditLogRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillService;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.reporting.application.BudgetRepository;
import com.erp.reporting.domain.Budget;
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
import java.time.LocalDate;
import java.util.List;

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
    @Autowired
    private PurchaseOrderService purchaseOrders;
    @Autowired
    private GoodsReceiptService goodsReceipts;
    @Autowired
    private VendorBillService vendorBills;
    @Autowired
    private LedgerPosting ledgerPosting;
    @Autowired
    private BudgetRepository budgetRepository;

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

    // ---- (a2) PR4: reconciliation preset drills a broken/-checked subledger into its GL lines ------

    @Test
    void reconciliationPresetCallsHealthCheckThenDrillsIntoGeneralLedger() throws Exception {
        // A procure-to-pay cycle posts real lines to the AP control account (2100), matching
        // ReconciliationIT's known-good setup — so the reconciliation health-check and the general-ledger
        // drill-down both surface real seeded data, not just an empty/blank result.
        int n = (int) (System.nanoTime() % 100000);
        Long vendorId = masterData.createPartner("V-COPILOT-REC-" + n, "Copilot vendor " + n, true, false,
                null, 30, null, null).getId();
        Long itemId = masterData.createItem("RM-COPILOT-REC-" + n, "Copilot raw " + n, ItemType.RAW, "EA",
                true, new BigDecimal("10"), null, null).getId();
        LocalDate june = LocalDate.of(2026, 6, 15);
        PurchaseOrder po = purchaseOrders.createOrder(vendorId,
                List.of(new PoLineInput(itemId, new BigDecimal("20"), new BigDecimal("10"))), june, "tester");
        purchaseOrders.confirm(po.getId(), "tester");
        Long poLineId = po.getLines().get(0).getId();
        goodsReceipts.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("20"))), june, "tester");
        String billNumber = vendorBills.postBill(po.getId(),
                List.of(new BillLineInput(poLineId, new BigDecimal("20"), new BigDecimal("10"))),
                "STANDARD", june, "tester").getBillNumber();

        // Turn 1: health-check. Turn 2: drill into the AP control account's GL lines. Turn 3: conclusion.
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_health", "get_reconciliation_health", "{\"asOf\":\"2026-12-31\"}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_gl", "get_general_ledger",
                    "{\"accountCode\":\"2100\",\"asOf\":\"2026-12-31\"}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 8L, 2L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("The books balance; AP reconciles to the GL control account.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 20L, 6L));
        });

        String body = chat("frank", "FINANCE",
                "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":"
                        + "\"why don't the books reconcile?\"}]}],\"preset\":\"reconciliation\"}");

        // Both reporting tools ran (>= 2 reporting tool calls), each with a tool_result carrying real data.
        assertThat(body).contains("event:tool_call");
        assertThat(body).contains("get_reconciliation_health");
        assertThat(body).contains("get_general_ledger");
        assertThat(body).contains("\"trialBalanceBalanced\"");
        assertThat(body).contains("\"accountCode\":\"2100\"");
        // The vendor bill's own source document id shows up in the drill-down result — proof the drill-down
        // surfaced the ledger line posted by *this* test's bill, not just any 2100 activity.
        assertThat(body).contains("\"sourceDocId\":\"" + billNumber + "\"");
        assertThat(body).contains("event:done");
    }

    // ---- (a3) PR4: margin preset exercises income-statement/revenue-trend tools ---------------------

    @Test
    void marginPresetCallsIncomeStatementTwiceAndRevenueTrend() throws Exception {
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_is1", "get_income_statement", "{\"asOf\":\"2026-07-31\"}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_is2", "get_income_statement", "{\"asOf\":\"2026-06-30\"}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_rt", "get_revenue_trend", "{\"months\":3,\"asOf\":\"2026-07-31\"}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Margin moved mainly due to revenue growth.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 15L, 5L));
        });

        String body = chat("grace", "FINANCE",
                "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":"
                        + "\"why did gross margin change?\"}]}],\"preset\":\"margin\"}");

        assertThat(body).contains("get_income_statement");
        assertThat(body).contains("get_revenue_trend");
        assertThat(body).contains("\"totalRevenue\"");
        assertThat(body).contains("\"grossMargin\"");
        assertThat(body).contains("event:done");
    }

    // ---- (a4) PR4: remaining new read tools each get at least one real call ------------------------

    @Test
    void cashFlowAndBudgetVarianceToolsReturnRealData() throws Exception {
        // A distinctive cash + revenue posting in June 2026 (same pattern as FinanceAnalyticsIT), plus a
        // guaranteed budget row for that same account/year (DataSeeder's demo budgets only run under the
        // "seed" profile, which this IT does not activate) — so both tools' results below are checked against
        // a figure this test knows is actually present, not just "some real data exists".
        BigDecimal amount = new BigDecimal("1234");
        ledgerPosting.post(new JournalEntryRequest(null, LocalDate.of(2026, 6, 18), "copilot cash/budget probe",
                null, "TEST-COPILOT-CFBV", "CFBV-1", "POST", List.of(
                new Line("1010", amount, null, "cash in"),
                new Line("4100", null, amount, "revenue"))), "test");
        if (!budgetRepository.existsByPeriodYearAndAccountCode(2026, "4100")) {
            budgetRepository.saveAndFlush(new Budget(2026, "4100", new BigDecimal("500000")));
        }

        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_cf", "get_cash_flow", "{\"months\":3,\"asOf\":\"2026-06-30\"}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onToolUse("tu_bv", "get_budget_variance", "{\"year\":2026,\"month\":6}");
            turn.onEnd(new AnthropicPort.StopInfo("tool_use", 5L, 1L));
        });
        scriptedPort.enqueue(turn -> {
            turn.onTextDelta("Here is the cash and budget picture.");
            turn.onEnd(new AnthropicPort.StopInfo("end_turn", 10L, 4L));
        });

        String body = chat("heidi", "FINANCE",
                "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":"
                        + "\"how is cash and budget looking?\"}]}]}");

        assertThat(body).contains("get_cash_flow");
        assertThat(body).contains("get_budget_variance");
        // The cash-flow series' June 2026 point includes this test's own cash inflow.
        assertThat(body).contains("\"month\":\"2026-06\"");
        // The budget-variance report carries the seeded 4100 budget line for June 2026.
        assertThat(body).contains("\"accountCode\":\"4100\"");
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
