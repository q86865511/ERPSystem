package com.erp.sales.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesOrder;
import com.erp.sales.domain.SalesOrderStatus;
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
 * Posting-rule integration test for customer invoices. Asserts an invoice posts Dr AR / Cr Revenue +
 * Cr Output VAT (revenue) and Dr COGS / Cr Deferred-COGS (cost), clears the Deferred-COGS the delivery
 * parked (1340 nets to zero across delivery + invoice), and reconciles the AR subledger to GL 1200.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SalesInvoicePostingIT {

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
    private ArSubledgerService arSubledgerService;
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

    /** Customer + finished good seeded with stock, an SO and a delivery of {@code shipQty}. */
    private SalesOrder deliveredOrder(String onHand, String cost, String orderQty, String price,
                                      String shipQty) {
        int n = SEQ.incrementAndGet();
        Long customerId = masterDataService.createPartner("C-INV-" + n, "Customer " + n, false, true,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("FG-INV-" + n, "Finished " + n, ItemType.FINISHED,
                "EA", true, new BigDecimal(cost), null, null).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal(onHand),
                new BigDecimal(cost), "seed stock", JUNE, "tester");
        SalesOrder so = salesOrderService.createOrder(customerId,
                List.of(new SoLineInput(itemId, new BigDecimal(orderQty), new BigDecimal(price))),
                JUNE, "tester");
        salesOrderService.confirm(so.getId(), "tester");
        deliveryService.deliver(so.getId(), stockLocationId(),
                List.of(new DeliveryLineInput(so.getLines().get(0).getId(), new BigDecimal(shipQty))),
                JUNE, "tester");
        return salesOrderService.getOrder(so.getId());
    }

    @Test
    void invoiceRecognisesRevenueVatAndCogsAndClearsDeferredCogs() {
        SalesOrder so = deliveredOrder("50", "10", "30", "20", "30");
        Long soLineId = so.getLines().get(0).getId();

        SalesInvoice invoice = salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("30"), new BigDecimal("20"))),
                "STANDARD", JUNE, "tester");

        Long jeId = invoice.getJournalEntryId();
        assertThat(jeId).isNotNull();
        // Revenue side: Dr 1200 630 / Cr 4100 600, Cr 2400 30.
        assertThat(debitFor(jeId, "1200")).isEqualByComparingTo("630");
        assertThat(creditFor(jeId, "4100")).isEqualByComparingTo("600");
        assertThat(creditFor(jeId, "2400")).isEqualByComparingTo("30");
        // COGS side: Dr 5100 300 / Cr 1340 300.
        assertThat(debitFor(jeId, "5100")).isEqualByComparingTo("300");
        assertThat(creditFor(jeId, "1340")).isEqualByComparingTo("300");
        // Customer tagged on AR + Deferred-COGS lines.
        assertThat(partnerOn(jeId, "1200")).isEqualTo(so.getPartnerId());

        // Deferred-COGS nets to zero across this order's delivery (Dr) + invoice (Cr).
        Long deliveryJeId = deliveryJeId(soLineId);
        assertThat(netForAccountInEntries("1340", deliveryJeId, jeId)).isEqualByComparingTo("0");

        // Invoice carried its amounts; AR subledger reconciles to GL 1200.
        assertThat(invoice.getGrossAmount()).isEqualByComparingTo("630");
        assertThat(invoice.getCogsAmount()).isEqualByComparingTo("300");
        assertThat(invoice.openBalance()).isEqualByComparingTo("630");
        assertThat(arSubledgerService.arSubledgerBalance()).isEqualByComparingTo(glBalance("1200"));
    }

    @Test
    void partialInvoicesMatchDeliveriesFifo() {
        SalesOrder so = deliveredOrder("100", "5", "100", "8", "100");
        Long soLineId = so.getLines().get(0).getId();

        SalesInvoice first = salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("40"), new BigDecimal("8"))),
                "STANDARD", JUNE, "tester");
        assertThat(first.getCogsAmount()).isEqualByComparingTo("200");   // 40 @ 5

        SalesInvoice second = salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("60"), new BigDecimal("8"))),
                "STANDARD", JUNE, "tester");
        assertThat(second.getCogsAmount()).isEqualByComparingTo("300");  // 60 @ 5

        // All delivered cost recognised; deferred-COGS for this order back to zero.
        Long deliveryJeId = deliveryJeId(soLineId);
        assertThat(netForAccountInEntries("1340", deliveryJeId, first.getJournalEntryId(),
                second.getJournalEntryId())).isEqualByComparingTo("0");
        SalesOrder reloaded = salesOrderService.getOrder(so.getId());
        assertThat(reloaded.getLines().get(0).getQtyInvoiced()).isEqualByComparingTo("100");
    }

    @Test
    void fullyShippedAndInvoicedOrderCloses() {
        SalesOrder so = deliveredOrder("50", "10", "30", "20", "30");
        Long soLineId = so.getLines().get(0).getId();
        assertThat(so.getStatus()).isEqualTo(SalesOrderStatus.SHIPPED);

        salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("30"), new BigDecimal("20"))),
                "STANDARD", JUNE, "tester");

        SalesOrder reloaded = salesOrderService.getOrder(so.getId());
        assertThat(reloaded.getStatus()).isEqualTo(SalesOrderStatus.CLOSED);
    }

    @Test
    void partiallyShippedOrderStaysOpenWhenItsShippedPortionIsInvoiced() {
        // Ordered 100, only 40 shipped: the SO is not fully shipped, so invoicing that 40 must not close it.
        SalesOrder so = deliveredOrder("100", "5", "100", "8", "40");
        Long soLineId = so.getLines().get(0).getId();
        assertThat(so.getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_SHIPPED);

        salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("40"), new BigDecimal("8"))),
                "STANDARD", JUNE, "tester");

        SalesOrder reloaded = salesOrderService.getOrder(so.getId());
        assertThat(reloaded.getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_SHIPPED);
    }

    @Test
    void blocksInvoicingBeyondDelivered() {
        SalesOrder so = deliveredOrder("100", "10", "30", "20", "10");
        Long soLineId = so.getLines().get(0).getId();

        assertThatThrownBy(() -> salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLineId, new BigDecimal("20"), new BigDecimal("20"))),
                "STANDARD", JUNE, "tester"))
                .isInstanceOf(SalesValidationException.class);
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

    private BigDecimal glBalance(String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = ? AND je.status = 'POSTED'",
                BigDecimal.class, accountCode);
    }

    private Long deliveryJeId(Long soLineId) {
        return jdbcTemplate.queryForObject(
                "SELECT journal_entry_id FROM delivery_line WHERE so_line_id = ? ORDER BY id ASC LIMIT 1",
                Long.class, soLineId);
    }

    /** Net (debit − credit) for an account across just the given journal entries. */
    private BigDecimal netForAccountInEntries(String accountCode, Long... journalEntryIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(journalEntryIds.length, "?"));
        Object[] args = new Object[journalEntryIds.length + 1];
        args[0] = accountCode;
        System.arraycopy(journalEntryIds, 0, args, 1, journalEntryIds.length);
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE a.code = ? AND jl.journal_entry_id IN (" + placeholders + ")",
                BigDecimal.class, args);
    }
}
