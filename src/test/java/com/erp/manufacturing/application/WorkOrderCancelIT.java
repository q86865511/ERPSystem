package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.inventory.domain.ItemCostState;
import com.erp.ledger.application.LedgerReportService;
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
 * Acceptance test for cancelling an in-progress work order. The issued components are returned to stock
 * (Dr raw inventory / Cr WIP at the issue cost), so the raw stock is restored, WIP nets to zero for the
 * order, the work order is CANCELLED and the trial balance still balances.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkOrderCancelIT {

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
    private LedgerReportService ledgerReportService;
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
    void cancelReturnsIssuedComponentsAndZeroesWip() {
        int n = SEQ.incrementAndGet();
        Long rawId = masterDataService.createItem("RM-CX-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();
        stockAdjustmentService.postAdjustment(rawId, stockLocationId(), new BigDecimal("100"),
                new BigDecimal("10"), "seed", JUNE, "tester");
        Long fgId = masterDataService.createItem("FG-CX-" + n, "Finished " + n, ItemType.FINISHED, "EA",
                true, BigDecimal.ZERO, null, null).getId();
        BillOfMaterials bom = bomService.createBom(fgId, new BigDecimal("1"),
                List.of(new ComponentInput(rawId, new BigDecimal("1"), null)), "tester");

        WorkOrder wo = workOrderService.create(fgId, bom.getId(), new BigDecimal("50"), "tester");
        workOrderService.release(wo.getId(), "tester");
        workOrderService.issue(wo.getId(), stockLocationId(), JUNE, "tester");

        // After issue: raw down to 50; cancel returns the 50 to stock.
        wo = workOrderService.cancel(wo.getId(), stockLocationId(), JUNE, "tester");

        // Return entry: Dr 1310 Raw / Cr 1320 WIP for 500.
        Long returnJeId = jeBySource(wo.getWoNumber() + "#return#1");
        assertThat(debitFor(returnJeId, "1310")).isEqualByComparingTo("500");
        assertThat(creditFor(returnJeId, "1320")).isEqualByComparingTo("500");

        // Raw stock restored; WIP nets to zero across the order; order cancelled; books balanced.
        ItemCostState raw = itemCostStateRepository.findById(rawId).orElseThrow();
        assertThat(raw.getOnHandQty()).isEqualByComparingTo("100");
        assertThat(raw.getTotalValue()).isEqualByComparingTo("1000");
        assertThat(wipNetForWorkOrder(wo.getWoNumber())).isEqualByComparingTo("0");
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(ledgerReportService.trialBalance().balanced()).isTrue();
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
