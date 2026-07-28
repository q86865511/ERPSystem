package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.inventory.domain.ItemCostState;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.WorkOrder;
import com.erp.manufacturing.domain.WorkOrderStatus;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Posting-rule integration test for work-order issue. Asserts releasing snapshots the BOM and issuing
 * consumes components into WIP — Dr WIP (1320) / Cr raw inventory (1310) at moving-average cost — and
 * accumulates the consumed cost for the completion roll-up.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkOrderIssueIT {

    private static final java.time.LocalDate JUNE = java.time.LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private BomService bomService;
    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    private Long rawWithStock(String onHand, String cost) {
        int n = SEQ.incrementAndGet();
        Long itemId = masterDataService.createItem("RM-WO-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal(cost), null, null).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal(onHand),
                new BigDecimal(cost), "seed stock", JUNE, "tester");
        return itemId;
    }

    private Long finishedItem() {
        int n = SEQ.incrementAndGet();
        return masterDataService.createItem("FG-WO-" + n, "Finished " + n, ItemType.FINISHED, "EA", true,
                BigDecimal.ZERO, null, null).getId();
    }

    @Test
    void issueConsumesComponentsIntoWip() {
        Long rawId = rawWithStock("100", "10");
        Long fgId = finishedItem();
        BillOfMaterials bom = bomService.createBom(fgId, new BigDecimal("1"),
                List.of(new ComponentInput(rawId, new BigDecimal("1"), null)), "tester");

        WorkOrder wo = workOrderService.create(fgId, bom.getId(), new BigDecimal("50"), "tester");
        wo = workOrderService.release(wo.getId(), "tester");
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);
        assertThat(wo.getComponents().get(0).getPlannedQty()).isEqualByComparingTo("50");

        wo = workOrderService.issue(wo.getId(), stockLocationId(), JUNE, "tester");

        // Dr 1320 WIP / Cr 1310 Raw for 500 (50 @ 10).
        Long jeId = wo.getComponents().get(0).getJournalEntryId();
        assertThat(jeId).isNotNull();
        assertThat(debitFor(jeId, "1320")).isEqualByComparingTo("500");
        assertThat(creditFor(jeId, "1310")).isEqualByComparingTo("500");

        // Work order recorded the consumed cost for the roll-up; status advanced.
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(wo.getTotalComponentCost()).isEqualByComparingTo("500");
        assertThat(wo.getComponents().get(0).getConsumedValue()).isEqualByComparingTo("500");

        // Raw stock dropped by what was issued (100 - 50 = 50 @ 10).
        ItemCostState cost = itemCostStateRepository.findById(rawId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("50");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("500");
    }

    @Test
    void blocksIssueBeyondOnHand() {
        Long rawId = rawWithStock("30", "10");   // only 30 on hand, BOM needs 50
        Long fgId = finishedItem();
        BillOfMaterials bom = bomService.createBom(fgId, new BigDecimal("1"),
                List.of(new ComponentInput(rawId, new BigDecimal("1"), null)), "tester");
        WorkOrder wo = workOrderService.create(fgId, bom.getId(), new BigDecimal("50"), "tester");
        workOrderService.release(wo.getId(), "tester");

        assertThatThrownBy(() -> workOrderService.issue(wo.getId(), stockLocationId(), JUNE, "tester"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void issueTakesCostLocksInItemIdOrderWhateverTheBomOrder() {
        // BOM lists its components in descending item id; ADR 0003's lock order says the issue must
        // still walk them ascending, so two work orders sharing components cannot deadlock.
        Long lowItem = rawWithStock("100", "10");
        Long highItem = rawWithStock("100", "10");
        assertThat(highItem).isGreaterThan(lowItem);
        Long fgId = finishedItem();
        BillOfMaterials bom = bomService.createBom(fgId, new BigDecimal("1"), List.of(
                new ComponentInput(highItem, new BigDecimal("1"), null),
                new ComponentInput(lowItem, new BigDecimal("1"), null)), "tester");

        WorkOrder wo = workOrderService.create(fgId, bom.getId(), new BigDecimal("10"), "tester");
        workOrderService.release(wo.getId(), "tester");
        wo = workOrderService.issue(wo.getId(), stockLocationId(), JUNE, "tester");

        // One STOCK leg per component, read back in insertion order.
        List<Long> lockedItems = jdbcTemplate.queryForList(
                "SELECT sle.item_id FROM stock_ledger_entry sle "
                        + "JOIN location l ON l.id = sle.location_id "
                        + "WHERE l.location_type = 'STOCK' AND sle.source_doc_type = 'WORK_ORDER' "
                        + "AND sle.source_doc_id LIKE ? ORDER BY sle.id",
                Long.class, wo.getWoNumber() + "#issue#%");
        assertThat(lockedItems).containsExactly(lowItem, highItem);
    }

    private BigDecimal debitFor(Long journalEntryId, String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE jl.journal_entry_id = ? AND a.code = ?",
                BigDecimal.class, journalEntryId, accountCode);
    }

    private BigDecimal creditFor(Long journalEntryId, String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE jl.journal_entry_id = ? AND a.code = ?",
                BigDecimal.class, journalEntryId, accountCode);
    }
}
