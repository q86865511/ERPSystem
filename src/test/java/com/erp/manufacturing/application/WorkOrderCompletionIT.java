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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posting-rule integration test for work-order completion. Asserts the finished goods are received at
 * rolled actual cost (Dr Finished Goods / Cr WIP), the WIP control account nets to zero across the
 * order's entries, and no variance arises for a clean roll-up.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkOrderCompletionIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
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

    @Test
    void completionReceivesFinishedGoodsAtRolledCostAndZeroesWip() {
        int n = SEQ.incrementAndGet();
        Long rawId = masterDataService.createItem("RM-WC-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();
        stockAdjustmentService.postAdjustment(rawId, stockLocationId(), new BigDecimal("100"),
                new BigDecimal("10"), "seed", JUNE, "tester");
        Long fgId = masterDataService.createItem("FG-WC-" + n, "Finished " + n, ItemType.FINISHED, "EA",
                true, BigDecimal.ZERO, null, null).getId();
        BillOfMaterials bom = bomService.createBom(fgId, new BigDecimal("1"),
                List.of(new ComponentInput(rawId, new BigDecimal("1"), null)), "tester");

        WorkOrder wo = workOrderService.create(fgId, bom.getId(), new BigDecimal("50"), "tester");
        workOrderService.release(wo.getId(), "tester");
        workOrderService.issue(wo.getId(), stockLocationId(), JUNE, "tester");
        wo = workOrderService.complete(wo.getId(), new BigDecimal("50"), stockLocationId(), JUNE, "tester");

        // Completion entry: Dr 1330 Finished Goods / Cr 1320 WIP for 500 (50 @ rolled 10).
        Long receiptJeId = jeBySource(wo.getWoNumber() + "#receipt");
        assertThat(debitFor(receiptJeId, "1330")).isEqualByComparingTo("500");
        assertThat(creditFor(receiptJeId, "1320")).isEqualByComparingTo("500");

        // Finished goods on hand at the rolled cost.
        ItemCostState fg = itemCostStateRepository.findById(fgId).orElseThrow();
        assertThat(fg.getOnHandQty()).isEqualByComparingTo("50");
        assertThat(fg.getTotalValue()).isEqualByComparingTo("500");
        assertThat(fg.getAvgUnitCost()).isEqualByComparingTo("10");

        // WIP nets to zero across the order's entries; clean roll-up posts no variance.
        assertThat(wipNetForWorkOrder(wo.getWoNumber())).isEqualByComparingTo("0");
        assertThat(varianceForWorkOrder(wo.getWoNumber())).isEqualByComparingTo("0");
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DONE);
    }

    private Long jeBySource(String sourceDocId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM journal_entry WHERE source_doc_type = 'WORK_ORDER' AND source_doc_id = ?",
                Long.class, sourceDocId);
    }

    private BigDecimal wipNetForWorkOrder(String woNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = '1320' AND je.source_doc_type = 'WORK_ORDER' "
                        + "AND je.source_doc_id LIKE ?",
                BigDecimal.class, woNumber + "%");
    }

    private BigDecimal varianceForWorkOrder(String woNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = '5930' AND je.source_doc_type = 'WORK_ORDER' "
                        + "AND je.source_doc_id LIKE ?",
                BigDecimal.class, woNumber + "%");
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
