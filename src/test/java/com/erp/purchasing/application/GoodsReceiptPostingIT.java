package com.erp.purchasing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.domain.ItemCostState;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.domain.GoodsReceipt;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.purchasing.domain.PurchaseOrderStatus;
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
 * Posting-rule integration test for goods receipts. Asserts a receipt posts Dr Inventory / Cr GR-IR
 * through the inventory port, moves the moving-average cost, and advances the purchase order's
 * received quantity and status. Unique vendor/item codes per test (the container is shared per class).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GoodsReceiptPostingIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
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

    private Long newVendor() {
        int n = SEQ.incrementAndGet();
        return masterDataService.createPartner("V-GR-" + n, "Vendor " + n, true, false, null, 30,
                null, null).getId();
    }

    private Long newRawItem(String price) {
        int n = SEQ.incrementAndGet();
        return masterDataService.createItem("RM-GR-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal(price), null, null).getId();
    }

    private PurchaseOrder confirmedOrder(Long itemId, String qty, String price) {
        PurchaseOrder po = purchaseOrderService.createOrder(newVendor(),
                List.of(new PoLineInput(itemId, new BigDecimal(qty), new BigDecimal(price))),
                JUNE, "tester");
        return purchaseOrderService.confirm(po.getId(), "tester");
    }

    @Test
    void receiptPostsInventoryAgainstGrIrAndAdvancesOrder() {
        Long itemId = newRawItem("10");
        PurchaseOrder po = confirmedOrder(itemId, "100", "10");
        Long poLineId = po.getLines().get(0).getId();

        GoodsReceipt grn = goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("100"))), JUNE, "tester");

        // Dr 1310 Inventory / Cr 2150 GR-IR for 1000.
        Long jeId = grn.getLines().get(0).getJournalEntryId();
        assertThat(jeId).isNotNull();
        assertThat(debitFor(jeId, "1310")).isEqualByComparingTo("1000");
        assertThat(creditFor(jeId, "2150")).isEqualByComparingTo("1000");

        // Moving-average cost state.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("100");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("1000");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");

        // Order fully received.
        PurchaseOrder reloaded = purchaseOrderRepository.findById(po.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(reloaded.getLines().get(0).getQtyReceived()).isEqualByComparingTo("100");
    }

    @Test
    void partialReceiptsAdvanceStatusIncrementally() {
        Long itemId = newRawItem("4");
        PurchaseOrder po = confirmedOrder(itemId, "100", "4");
        Long poLineId = po.getLines().get(0).getId();
        Long stock = stockLocationId();

        goodsReceiptService.receive(po.getId(), stock,
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("40"))), JUNE, "tester");
        assertThat(purchaseOrderRepository.findById(po.getId()).orElseThrow().getStatus())
                .isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);

        goodsReceiptService.receive(po.getId(), stock,
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("60"))), JUNE, "tester");
        assertThat(purchaseOrderRepository.findById(po.getId()).orElseThrow().getStatus())
                .isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void blocksOverReceipt() {
        Long itemId = newRawItem("10");
        PurchaseOrder po = confirmedOrder(itemId, "100", "10");
        Long poLineId = po.getLines().get(0).getId();

        assertThatThrownBy(() -> goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("101"))), JUNE, "tester"))
                .isInstanceOf(PurchasingValidationException.class);
    }

    @Test
    void multiLineReceiptTakesCostLocksInItemIdOrder() {
        // Two items, PO lines deliberately in descending item id — ADR 0003's lock order says the stock
        // legs (each of which locks that item's cost state) must still come out ascending.
        Long lowItem = newRawItem("10");
        Long highItem = newRawItem("10");
        assertThat(highItem).isGreaterThan(lowItem);
        PurchaseOrder po = purchaseOrderService.createOrder(newVendor(), List.of(
                new PoLineInput(highItem, new BigDecimal("10"), new BigDecimal("10")),
                new PoLineInput(lowItem, new BigDecimal("10"), new BigDecimal("10"))), JUNE, "tester");
        purchaseOrderService.confirm(po.getId(), "tester");

        GoodsReceipt grn = goodsReceiptService.receive(po.getId(), stockLocationId(), List.of(
                new ReceiptLineInput(po.getLines().get(0).getId(), new BigDecimal("10")),
                new ReceiptLineInput(po.getLines().get(1).getId(), new BigDecimal("10"))),
                JUNE, "tester");

        // One STOCK leg per receipt line, read back in insertion order.
        List<Long> lockedItems = jdbcTemplate.queryForList(
                "SELECT sle.item_id FROM stock_ledger_entry sle "
                        + "JOIN location l ON l.id = sle.location_id "
                        + "WHERE l.location_type = 'STOCK' AND sle.source_doc_type = 'GOODS_RECEIPT' "
                        + "AND sle.source_doc_id LIKE ? ORDER BY sle.id",
                Long.class, grn.getGrnNumber() + "#%");
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
