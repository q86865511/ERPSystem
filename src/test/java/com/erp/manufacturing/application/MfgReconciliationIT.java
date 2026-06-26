package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.InventoryReportService;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.inventory.domain.ItemCostState;
import com.erp.ledger.application.LedgerReportService;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.WorkOrder;
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
 * Manufacturing acceptance test. Runs BOM → Work Order → issue → complete and asserts the books
 * reconcile: raw inventory falls, finished goods rise at the rolled actual cost, the WIP control
 * account nets to zero, the inventory subledger equals its GL control accounts, and the trial balance
 * balances.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MfgReconciliationIT {

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
    private InventoryReportService inventoryReportService;
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
    void makeChainReconciles() {
        int n = SEQ.incrementAndGet();
        Long rawId = masterDataService.createItem("RM-MR-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();
        stockAdjustmentService.postAdjustment(rawId, stockLocationId(), new BigDecimal("100"),
                new BigDecimal("10"), "seed", JUNE, "tester");
        Long fgId = masterDataService.createItem("FG-MR-" + n, "Finished " + n, ItemType.FINISHED, "EA",
                true, BigDecimal.ZERO, null, null).getId();
        BillOfMaterials bom = bomService.createBom(fgId, new BigDecimal("1"),
                List.of(new ComponentInput(rawId, new BigDecimal("1"), null)), "tester");

        WorkOrder wo = workOrderService.create(fgId, bom.getId(), new BigDecimal("50"), "tester");
        workOrderService.release(wo.getId(), "tester");
        workOrderService.issue(wo.getId(), stockLocationId(), JUNE, "tester");
        wo = workOrderService.complete(wo.getId(), new BigDecimal("50"), stockLocationId(), JUNE, "tester");

        // Raw fell by 50 (100 - 50); finished goods rose by 50 at rolled cost 10.
        ItemCostState raw = itemCostStateRepository.findById(rawId).orElseThrow();
        assertThat(raw.getOnHandQty()).isEqualByComparingTo("50");
        assertThat(raw.getTotalValue()).isEqualByComparingTo("500");
        ItemCostState fg = itemCostStateRepository.findById(fgId).orElseThrow();
        assertThat(fg.getOnHandQty()).isEqualByComparingTo("50");
        assertThat(fg.getTotalValue()).isEqualByComparingTo("500");

        // WIP control nets to zero across the work order's entries.
        assertThat(wipNetForWorkOrder(wo.getWoNumber())).isEqualByComparingTo("0");

        // Inventory subledger == GL inventory control accounts; trial balance balances.
        inventoryReportService.subledgerByInventoryAccount().forEach(line ->
                assertThat(line.subledgerValue())
                        .as("subledger == GL for account %s", line.accountCode())
                        .isEqualByComparingTo(glBalance(line.accountCode())));
        assertThat(ledgerReportService.trialBalance().balanced()).isTrue();
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

    private BigDecimal glBalance(String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = ? AND je.status = 'POSTED'",
                BigDecimal.class, accountCode);
    }
}
