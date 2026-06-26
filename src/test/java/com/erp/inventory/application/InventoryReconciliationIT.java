package com.erp.inventory.application;

import com.erp.TestcontainersConfiguration;
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
 * The Phase 1 acceptance test: the inventory subledger reconciles to the General Ledger. After posting
 * adjustments it asserts (a) for every inventory control account, the subledger value equals the GL
 * account balance, and (b) each item's moving-average cache equals the sum of its STOCK-leg value
 * deltas (the source of truth). Both are system-wide invariants, robust to accumulated test data.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InventoryReconciliationIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SKU_SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private InventoryReportService inventoryReportService;
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

    private Long newItem(ItemType type) {
        return masterDataService.createItem("REC-" + type + "-" + SKU_SEQ.incrementAndGet(),
                "Recon " + type, type, "EA", true, new BigDecimal("5"), null, null).getId();
    }

    @Test
    void inventorySubledgerReconcilesToGeneralLedger() {
        Long rawId = newItem(ItemType.RAW);
        Long finishedId = newItem(ItemType.FINISHED);
        Long stock = stockLocationId();

        stockAdjustmentService.postAdjustment(rawId, stock, new BigDecimal("100"),
                new BigDecimal("10"), "raw in", JUNE, "tester");
        stockAdjustmentService.postAdjustment(finishedId, stock, new BigDecimal("50"),
                new BigDecimal("8"), "fg in", JUNE, "tester");
        stockAdjustmentService.postAdjustment(rawId, stock, new BigDecimal("-30"),
                null, "raw out", JUNE, "tester");

        // (a) subledger value per inventory account == GL balance of that account.
        List<AccountSubledgerValue> subledger = inventoryReportService.subledgerByInventoryAccount();
        assertThat(subledger).isNotEmpty();
        for (AccountSubledgerValue line : subledger) {
            assertThat(line.subledgerValue())
                    .as("subledger == GL for account %s", line.accountCode())
                    .isEqualByComparingTo(glBalance(line.accountCode()));
        }

        // (b) per-item cache (ItemCostState.total_value) == SUM(value_delta) of STOCK legs.
        assertCacheMatchesStockLegs(rawId);
        assertCacheMatchesStockLegs(finishedId);
    }

    private void assertCacheMatchesStockLegs(Long itemId) {
        BigDecimal cache = itemCostStateRepository.findById(itemId).orElseThrow().getTotalValue();
        BigDecimal subledger = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(sle.value_delta), 0) FROM stock_ledger_entry sle "
                        + "JOIN location l ON l.id = sle.location_id "
                        + "WHERE l.location_type = 'STOCK' AND sle.item_id = ?",
                BigDecimal.class, itemId);
        assertThat(cache).as("cache == stock-leg sum for item %s", itemId)
                .isEqualByComparingTo(subledger);
    }

    /** GL balance for an account = SUM(debit - credit) over POSTED journal lines. */
    private BigDecimal glBalance(String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = ? AND je.status = 'POSTED'",
                BigDecimal.class, accountCode);
    }
}
