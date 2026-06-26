package com.erp.purchasing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.domain.ItemCostState;
import com.erp.ledger.application.LedgerReportService;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.payments.application.PaymentService;
import com.erp.payments.application.PaymentService.Allocation;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.GoodsReceipt;
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
 * The Phase 2 acceptance test: a full procure-to-pay chain reconciles. After PO → GR → VendorBill →
 * Payment it asserts the chain's GR-IR nets to zero, the vendor's AP nets to zero, the AP subledger
 * equals the GL 2100 control, the trial balance balances, and inventory rose by the receipt.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApReconciliationIT {

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
    private PaymentService paymentService;
    @Autowired
    private ApSubledgerService apSubledgerService;
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
    void procureToPayChainReconciles() {
        int n = SEQ.incrementAndGet();
        Long vendorId = masterDataService.createPartner("V-REC-" + n, "Vendor " + n, true, false,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("RM-REC-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();

        PurchaseOrder po = purchaseOrderService.createOrder(vendorId,
                List.of(new PoLineInput(itemId, new BigDecimal("100"), new BigDecimal("10"))), JUNE, "tester");
        Long poLineId = po.getLines().get(0).getId();
        purchaseOrderService.confirm(po.getId(), "tester");

        GoodsReceipt grn = goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("100"))), JUNE, "tester");
        Long grnJeId = grn.getLines().get(0).getJournalEntryId();

        VendorBill bill = vendorBillService.postBill(po.getId(),
                List.of(new BillLineInput(poLineId, new BigDecimal("100"), new BigDecimal("10"))),
                "STANDARD", JUNE, "tester");
        Long billJeId = bill.getJournalEntryId();

        paymentService.payOut(vendorId, bill.getGrossAmount(), JUNE,
                List.of(new Allocation(bill.getId(), bill.getGrossAmount())), "tester");

        // GR-IR for this chain (receipt credit cleared by the bill debit) nets to zero.
        BigDecimal grIrNet = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE a.code = '2150' AND jl.journal_entry_id IN (?, ?)",
                BigDecimal.class, grnJeId, billJeId);
        assertThat(grIrNet).as("GR-IR nets to zero for the chain").isEqualByComparingTo("0");

        // The vendor's AP (bill credit, payment debit) nets to zero.
        BigDecimal apForVendor = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.credit - jl.debit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE a.code = '2100' AND jl.partner_id = ?",
                BigDecimal.class, vendorId);
        assertThat(apForVendor).as("AP nets to zero for the vendor").isEqualByComparingTo("0");

        // AP subledger equals the GL AP control account (system-wide invariant).
        assertThat(apSubledgerService.apSubledgerBalance())
                .as("AP subledger == GL 2100").isEqualByComparingTo(apControlBalance());

        // The books balance.
        assertThat(ledgerReportService.trialBalance().balanced()).isTrue();

        // Inventory rose by the receipt.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("100");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("1000");
    }

    private BigDecimal apControlBalance() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.credit - jl.debit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = '2100' AND je.status = 'POSTED'",
                BigDecimal.class);
    }
}
