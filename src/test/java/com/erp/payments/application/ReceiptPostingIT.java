package com.erp.payments.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.payments.application.PaymentService.ReceiptAllocation;
import com.erp.payments.domain.Payment;
import com.erp.sales.application.DeliveryService;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.application.SalesInvoiceRepository;
import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesOrder;
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
 * Posting-rule integration test for customer receipts. Asserts an incoming payment posts Dr Cash /
 * Cr AR (tagged with the customer) and advances the matched invoice to PARTIALLY_PAID / PAID.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReceiptPostingIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private SalesOrderService salesOrderService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private SalesInvoiceService salesInvoiceService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;
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

    /** A posted customer invoice (gross 630): 30 FG @ cost 10, sales price 20, 5% VAT. */
    private SalesInvoice invoicedOrder() {
        int n = SEQ.incrementAndGet();
        Long customerId = masterDataService.createPartner("C-REC-" + n, "Customer " + n, false, true,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("FG-REC-" + n, "Finished " + n, ItemType.FINISHED,
                "EA", true, new BigDecimal("10"), null, null).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal("50"),
                new BigDecimal("10"), "seed stock", JUNE, "tester");
        SalesOrder so = salesOrderService.createOrder(customerId,
                List.of(new SoLineInput(itemId, new BigDecimal("30"), new BigDecimal("20"))),
                JUNE, "tester");
        salesOrderService.confirm(so.getId(), "tester");
        Long soLineId = so.getLines().get(0).getId();
        deliveryService.deliver(so.getId(), stockLocationId(),
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("30"))), JUNE, "tester");
        return salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("30"), new BigDecimal("20"))),
                "STANDARD", JUNE, "tester");
    }

    @Test
    void fullReceiptPostsCashArAndClosesInvoice() {
        SalesInvoice invoice = invoicedOrder();
        BigDecimal gross = invoice.getGrossAmount();

        Payment payment = paymentService.payIn(invoice.getPartnerId(), gross, JUNE,
                List.of(new ReceiptAllocation(invoice.getId(), gross)), "tester");

        Long jeId = payment.getJournalEntryId();
        assertThat(debitFor(jeId, "1010")).isEqualByComparingTo(gross);
        assertThat(creditFor(jeId, "1200")).isEqualByComparingTo(gross);
        assertThat(partnerOn(jeId, "1200")).isEqualTo(invoice.getPartnerId());

        SalesInvoice reloaded = salesInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("PAID");
        assertThat(reloaded.openBalance()).isEqualByComparingTo("0");
    }

    @Test
    void partialThenFullReceiptAdvancesStatus() {
        SalesInvoice invoice = invoicedOrder();

        paymentService.payIn(invoice.getPartnerId(), new BigDecimal("400"), JUNE,
                List.of(new ReceiptAllocation(invoice.getId(), new BigDecimal("400"))), "tester");
        assertThat(salesInvoiceRepository.findById(invoice.getId()).orElseThrow().getStatus().name())
                .isEqualTo("PARTIALLY_PAID");

        paymentService.payIn(invoice.getPartnerId(), new BigDecimal("230"), JUNE,
                List.of(new ReceiptAllocation(invoice.getId(), new BigDecimal("230"))), "tester");
        assertThat(salesInvoiceRepository.findById(invoice.getId()).orElseThrow().getStatus().name())
                .isEqualTo("PAID");
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
