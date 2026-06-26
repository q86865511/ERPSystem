package com.erp.sales.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.inventory.domain.ItemCostState;
import com.erp.ledger.application.LedgerReportService;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.payments.application.PaymentService;
import com.erp.payments.application.PaymentService.ReceiptAllocation;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.Delivery;
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
 * Order-to-cash acceptance test (mirror of ApReconciliationIT). Runs the full chain
 * SO → Delivery → Invoice → Receipt and asserts the books reconcile: inventory dropped, COGS + revenue
 * + Output VAT booked, the Deferred-COGS clearing nets to zero, AR nets to zero for the customer after
 * the receipt, the trial balance balances, and the AR subledger equals the GL 1200 control account.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ArReconciliationIT {

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
    private ArSubledgerService arSubledgerService;
    @Autowired
    private LedgerReportService ledgerReportService;
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
    void orderToCashChainReconciles() {
        int n = SEQ.incrementAndGet();
        Long customerId = masterDataService.createPartner("C-AREC-" + n, "Customer " + n, false, true,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("FG-AREC-" + n, "Finished " + n, ItemType.FINISHED,
                "EA", true, new BigDecimal("10"), null, null).getId();
        // Seed 100 FG @ 10 on hand.
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal("100"),
                new BigDecimal("10"), "seed stock", JUNE, "tester");

        // SO 30 @ 20 → deliver 30 → invoice 30 → receipt 630.
        SalesOrder so = salesOrderService.createOrder(customerId,
                List.of(new SoLineInput(itemId, new BigDecimal("30"), new BigDecimal("20"))),
                JUNE, "tester");
        salesOrderService.confirm(so.getId(), "tester");
        Long soLineId = so.getLines().get(0).getId();

        Delivery delivery = deliveryService.deliver(so.getId(), stockLocationId(),
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("30"))), JUNE, "tester");
        Long deliveryJeId = delivery.getLines().get(0).getJournalEntryId();

        SalesInvoice invoice = salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("30"), new BigDecimal("20"))),
                "STANDARD", JUNE, "tester");
        Long invoiceJeId = invoice.getJournalEntryId();

        paymentService.payIn(customerId, invoice.getGrossAmount(), JUNE,
                List.of(new ReceiptAllocation(invoice.getId(), invoice.getGrossAmount())), "tester");

        // Inventory dropped by the 30 shipped (100 - 30 = 70 @ 10).
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("70");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("700");

        // Revenue, Output VAT and COGS booked on the invoice entry.
        assertThat(creditFor(invoiceJeId, "4100")).isEqualByComparingTo("600");
        assertThat(creditFor(invoiceJeId, "2400")).isEqualByComparingTo("30");
        assertThat(debitFor(invoiceJeId, "5100")).isEqualByComparingTo("300");

        // Deferred-COGS nets to zero across delivery (Dr) + invoice (Cr).
        assertThat(netForAccount("1340", deliveryJeId, invoiceJeId)).isEqualByComparingTo("0");

        // AR nets to zero for the customer after the receipt (invoice Dr 630, receipt Cr 630).
        assertThat(arForCustomer(customerId)).isEqualByComparingTo("0");

        // Trial balance balances; AR subledger == GL 1200 control.
        assertThat(ledgerReportService.trialBalance().balanced()).isTrue();
        assertThat(arSubledgerService.arSubledgerBalance()).isEqualByComparingTo(glBalance("1200"));
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

    private BigDecimal netForAccount(String accountCode, Long jeA, Long jeB) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE a.code = ? AND jl.journal_entry_id IN (?, ?)",
                BigDecimal.class, accountCode, jeA, jeB);
    }

    private BigDecimal arForCustomer(Long customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE a.code = '1200' AND jl.partner_id = ?",
                BigDecimal.class, customerId);
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
