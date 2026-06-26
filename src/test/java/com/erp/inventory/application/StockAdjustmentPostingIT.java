package com.erp.inventory.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.domain.ItemCostState;
import com.erp.inventory.domain.StockAdjustment;
import com.erp.inventory.domain.StockLedgerEntry;
import com.erp.ledger.application.PeriodNotOpenException;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.masterdata.domain.Item;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Posting-rule integration tests for stock adjustments against a real PostgreSQL (Testcontainers).
 * Asserts the exact Dr/Cr lines, the two immutable subledger legs, the moving-average cost state, the
 * negative-inventory block, and that a closed-period rejection rolls back the whole cross-module post.
 * Each test uses a unique SKU (tests commit; the container is shared per class).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StockAdjustmentPostingIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SKU_SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
    @Autowired
    private StockLedgerEntryRepository stockLedgerEntryRepository;
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

    private Long newRawItem() {
        Item item = masterDataService.createItem("RM-ADJ-" + SKU_SEQ.incrementAndGet(),
                "Raw adj", ItemType.RAW, "EA", true, new BigDecimal("10"), null, null);
        return item.getId();
    }

    @Test
    void gainPostsDebitInventoryCreditAdjustmentAndRaisesAverage() {
        Long itemId = newRawItem();

        StockAdjustment adj = stockAdjustmentService.postAdjustment(
                itemId, stockLocationId(), new BigDecimal("100"), new BigDecimal("10"),
                "found stock", JUNE, "tester");

        // Journal entry: Dr 1310 / Cr 6000 for 1000.
        Long jeId = adj.getJournalEntryId();
        assertThat(jeId).isNotNull();
        assertThat(debitFor(jeId, "1310")).isEqualByComparingTo("1000");
        assertThat(creditFor(jeId, "6000")).isEqualByComparingTo("1000");

        // Two paired subledger legs.
        List<StockLedgerEntry> legs = stockLedgerEntryRepository
                .findByMovementGroupId(adj.getMovementGroupId());
        assertThat(legs).hasSize(2);
        assertThat(legs.stream().map(StockLedgerEntry::getValueDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");

        // Cost state.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("100");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("1000");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");
    }

    @Test
    void lossPostsDebitAdjustmentCreditInventoryAtAverage() {
        Long itemId = newRawItem();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal("100"),
                new BigDecimal("10"), "initial", JUNE, "tester");

        StockAdjustment loss = stockAdjustmentService.postAdjustment(
                itemId, stockLocationId(), new BigDecimal("-30"), null, "breakage", JUNE, "tester");

        Long jeId = loss.getJournalEntryId();
        assertThat(debitFor(jeId, "6000")).isEqualByComparingTo("300");
        assertThat(creditFor(jeId, "1310")).isEqualByComparingTo("300");

        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("70");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("700");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");
    }

    @Test
    void blocksLossThatWouldGoNegative() {
        Long itemId = newRawItem();

        assertThatThrownBy(() -> stockAdjustmentService.postAdjustment(
                itemId, stockLocationId(), new BigDecimal("-5"), null, "oops", JUNE, "tester"))
                .isInstanceOf(NegativeInventoryException.class);

        // Nothing committed: no cost state, no legs.
        assertThat(itemCostStateRepository.findById(itemId)).isEmpty();
        assertThat(stockLedgerEntryRepository.findByItemId(itemId)).isEmpty();
    }

    @Test
    void closedPeriodRejectionRollsBackTheWholeMovement() {
        Long itemId = newRawItem();

        assertThatThrownBy(() -> stockAdjustmentService.postAdjustment(
                itemId, stockLocationId(), new BigDecimal("100"), new BigDecimal("10"),
                "future", LocalDate.of(2099, 1, 1), "tester"))
                .isInstanceOf(PeriodNotOpenException.class);

        // The ledger rejection rolled back the inventory legs and cost state too.
        assertThat(itemCostStateRepository.findById(itemId)).isEmpty();
        assertThat(stockLedgerEntryRepository.findByItemId(itemId)).isEmpty();
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
