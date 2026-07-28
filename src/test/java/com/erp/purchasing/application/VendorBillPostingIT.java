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
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.purchasing.domain.VendorBill;
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
 * Posting-rule integration test for vendor bills. Asserts a clean bill clears GR-IR, records Input VAT
 * and the AP credit (partner-tagged), and that a price-variance bill routes the difference to inventory
 * and moves the moving average through a revaluation. Also checks the AP subledger equals the GL 2100
 * control after posting.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VendorBillPostingIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private VendorBillService vendorBillService;
    @Autowired
    private ApSubledgerService apSubledgerService;
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

    /** Creates a vendor + raw item, a confirmed PO, and receives the full quantity. Returns [poId, poLineId, vendorId]. */
    private long[] receivedOrder(String qty, String price) {
        int n = SEQ.incrementAndGet();
        Long vendorId = masterDataService.createPartner("V-VB-" + n, "Vendor " + n, true, false,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("RM-VB-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal(price), null, null).getId();
        PurchaseOrder po = purchaseOrderService.createOrder(vendorId,
                List.of(new PoLineInput(itemId, new BigDecimal(qty), new BigDecimal(price))),
                JUNE, "tester");
        Long poLineId = po.getLines().get(0).getId();
        purchaseOrderService.confirm(po.getId(), "tester");
        goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal(qty))), JUNE, "tester");
        return new long[]{po.getId(), poLineId, vendorId, itemId};
    }

    @Test
    void cleanBillClearsGrIrRecordsVatAndAp() {
        long[] ctx = receivedOrder("100", "10");
        long poId = ctx[0];
        long poLineId = ctx[1];
        long vendorId = ctx[2];
        long itemId = ctx[3];

        VendorBill bill = vendorBillService.postBill(poId,
                List.of(new BillLineInput(poLineId, new BigDecimal("100"), new BigDecimal("10"))),
                "STANDARD", JUNE, "tester");

        Long jeId = bill.getJournalEntryId();
        assertThat(debitFor(jeId, "2150")).isEqualByComparingTo("1000");   // clear GR-IR
        assertThat(debitFor(jeId, "1450")).isEqualByComparingTo("50");     // input VAT
        assertThat(creditFor(jeId, "2100")).isEqualByComparingTo("1050");  // accounts payable
        assertThat(debitFor(jeId, "1310")).isEqualByComparingTo("0");      // no variance

        // Partner dimension on the AP and GR-IR lines.
        assertThat(partnerOn(jeId, "2100")).isEqualTo(vendorId);
        assertThat(partnerOn(jeId, "2150")).isEqualTo(vendorId);

        assertThat(bill.getGrossAmount()).isEqualByComparingTo("1050");
        assertThat(bill.getStatus().name()).isEqualTo("POSTED");

        // Clean bill does not move the moving average.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("1000");

        // AP subledger reconciles to the GL AP control.
        assertThat(apSubledgerService.apSubledgerBalance()).isEqualByComparingTo(apControlBalance());
    }

    @Test
    void priceVarianceRoutesToInventoryAndMovesAverage() {
        long[] ctx = receivedOrder("100", "10");
        long poId = ctx[0];
        long poLineId = ctx[1];
        long itemId = ctx[3];

        // Bill at 11 vs receipt cost 10 → +100 variance to inventory.
        VendorBill bill = vendorBillService.postBill(poId,
                List.of(new BillLineInput(poLineId, new BigDecimal("100"), new BigDecimal("11"))),
                "STANDARD", JUNE, "tester");

        Long jeId = bill.getJournalEntryId();
        assertThat(debitFor(jeId, "2150")).isEqualByComparingTo("1000");  // clear GR-IR at receipt cost
        assertThat(debitFor(jeId, "1310")).isEqualByComparingTo("100");   // price variance to inventory
        assertThat(debitFor(jeId, "1450")).isEqualByComparingTo("55");    // VAT on 1100
        assertThat(creditFor(jeId, "2100")).isEqualByComparingTo("1155"); // AP gross

        // Moving average revalued from 10 to 11.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getTotalValue()).isEqualByComparingTo("1100");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("11");

        // A zero-quantity revaluation leg carries the variance in the subledger.
        BigDecimal revalueLegs = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(value_delta), 0) FROM stock_ledger_entry "
                        + "WHERE item_id = ? AND qty_delta = 0", BigDecimal.class, itemId);
        assertThat(revalueLegs).isEqualByComparingTo("100");
    }

    @Test
    void offsettingVariancesOnTheSameInventoryAccountPostWithoutAZeroLine() {
        // Two RAW items → the same inventory account 1310. Bill one 10% over and the other 10% under:
        // the variances cancel on 1310, and a zero-amount leg would be rejected by the ledger.
        int n = SEQ.incrementAndGet();
        Long vendorId = masterDataService.createPartner("V-OFF-" + n, "Vendor off " + n, true, false,
                null, 30, null, null).getId();
        Long itemA = masterDataService.createItem("RM-OFF-A-" + n, "Raw A " + n, ItemType.RAW, "EA", true,
                new BigDecimal("100"), null, null).getId();
        Long itemB = masterDataService.createItem("RM-OFF-B-" + n, "Raw B " + n, ItemType.RAW, "EA", true,
                new BigDecimal("100"), null, null).getId();
        PurchaseOrder po = purchaseOrderService.createOrder(vendorId, List.of(
                new PoLineInput(itemA, new BigDecimal("10"), new BigDecimal("100")),
                new PoLineInput(itemB, new BigDecimal("10"), new BigDecimal("100"))), JUNE, "tester");
        Long lineA = po.getLines().get(0).getId();
        Long lineB = po.getLines().get(1).getId();
        purchaseOrderService.confirm(po.getId(), "tester");
        goodsReceiptService.receive(po.getId(), stockLocationId(), List.of(
                new ReceiptLineInput(lineA, new BigDecimal("10")),
                new ReceiptLineInput(lineB, new BigDecimal("10"))), JUNE, "tester");

        VendorBill bill = vendorBillService.postBill(po.getId(), List.of(
                new BillLineInput(lineA, new BigDecimal("10"), new BigDecimal("110")),
                new BillLineInput(lineB, new BigDecimal("10"), new BigDecimal("90"))),
                "STANDARD", JUNE, "tester");

        Long jeId = bill.getJournalEntryId();
        assertThat(debitFor(jeId, "2150")).isEqualByComparingTo("2000");  // GR-IR at receipt cost
        assertThat(debitFor(jeId, "1450")).isEqualByComparingTo("100");   // VAT on 2000
        assertThat(creditFor(jeId, "2100")).isEqualByComparingTo("2100"); // AP gross
        // The netted variance carries no leg at all — neither a zero debit nor a zero credit.
        assertThat(debitFor(jeId, "1310")).isEqualByComparingTo("0");
        assertThat(creditFor(jeId, "1310")).isEqualByComparingTo("0");
        assertThat(lineCount(jeId, "1310")).isZero();

        // Each item is still revalued individually: A up 100, B down 100.
        assertThat(itemCostStateRepository.findById(itemA).orElseThrow().getTotalValue())
                .isEqualByComparingTo("1100");
        assertThat(itemCostStateRepository.findById(itemB).orElseThrow().getTotalValue())
                .isEqualByComparingTo("900");
    }

    private Integer lineCount(Long journalEntryId, String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE jl.journal_entry_id = ? AND a.code = ?",
                Integer.class, journalEntryId, accountCode);
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

    private Long partnerOn(Long journalEntryId, String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT jl.partner_id FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE jl.journal_entry_id = ? AND a.code = ?",
                Long.class, journalEntryId, accountCode);
    }

    /** GL Accounts Payable (2100) control balance as a credit-normal amount. */
    private BigDecimal apControlBalance() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.credit - jl.debit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = '2100' AND je.status = 'POSTED'",
                BigDecimal.class);
    }
}
