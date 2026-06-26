package com.erp.payments.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.payments.application.PaymentService.Allocation;
import com.erp.payments.domain.Payment;
import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillRepository;
import com.erp.purchasing.application.VendorBillService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Posting-rule integration test for outgoing payments: a payment posts Dr AP / Cr Cash (AP tagged with
 * the vendor) and its allocation advances the bill to PARTIALLY_PAID / PAID.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentPostingIT {

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
    private VendorBillRepository vendorBillRepository;
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

    /** Builds and posts PO → GR → clean bill; returns the bill (gross qty*price plus 5% VAT). */
    private VendorBill billedChain(String qty, String price) {
        int n = SEQ.incrementAndGet();
        Long vendorId = masterDataService.createPartner("V-PAY-" + n, "Vendor " + n, true, false,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("RM-PAY-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal(price), null, null).getId();
        PurchaseOrder po = purchaseOrderService.createOrder(vendorId,
                List.of(new PoLineInput(itemId, new BigDecimal(qty), new BigDecimal(price))), JUNE, "tester");
        Long poLineId = po.getLines().get(0).getId();
        purchaseOrderService.confirm(po.getId(), "tester");
        goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal(qty))), JUNE, "tester");
        return vendorBillService.postBill(po.getId(),
                List.of(new BillLineInput(poLineId, new BigDecimal(qty), new BigDecimal(price))),
                "STANDARD", JUNE, "tester");
    }

    @Test
    void fullPaymentPostsApCashAndClosesBill() {
        VendorBill bill = billedChain("100", "10"); // gross 1050
        BigDecimal gross = bill.getGrossAmount();

        Payment payment = paymentService.payOut(bill.getPartnerId(), gross, JUNE,
                List.of(new Allocation(bill.getId(), gross)), "tester");

        Long jeId = payment.getJournalEntryId();
        assertThat(debitFor(jeId, "2100")).isEqualByComparingTo(gross);  // AP down
        assertThat(creditFor(jeId, "1010")).isEqualByComparingTo(gross); // cash down
        assertThat(partnerOn(jeId, "2100")).isEqualTo(bill.getPartnerId());

        VendorBill reloaded = vendorBillRepository.findById(bill.getId()).orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("PAID");
        assertThat(reloaded.openBalance()).isEqualByComparingTo("0");
    }

    @Test
    void partialThenFullPaymentAdvancesStatus() {
        VendorBill bill = billedChain("100", "10"); // gross 1050

        paymentService.payOut(bill.getPartnerId(), new BigDecimal("600"), JUNE,
                List.of(new Allocation(bill.getId(), new BigDecimal("600"))), "tester");
        assertThat(vendorBillRepository.findById(bill.getId()).orElseThrow().getStatus().name())
                .isEqualTo("PARTIALLY_PAID");

        paymentService.payOut(bill.getPartnerId(), new BigDecimal("450"), JUNE,
                List.of(new Allocation(bill.getId(), new BigDecimal("450"))), "tester");
        assertThat(vendorBillRepository.findById(bill.getId()).orElseThrow().getStatus().name())
                .isEqualTo("PAID");
    }

    @Test
    void rejectsAllocationsThatDoNotSumToAmount() {
        VendorBill bill = billedChain("100", "10");

        assertThatThrownBy(() -> paymentService.payOut(bill.getPartnerId(), new BigDecimal("1050"),
                JUNE, List.of(new Allocation(bill.getId(), new BigDecimal("600"))), "tester"))
                .isInstanceOf(PaymentValidationException.class);
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
}
