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
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.CustomerReturn;
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
 * Acceptance test for customer returns (credit notes). A full return of a delivered-and-invoiced (but
 * unpaid) order brings stock back, reverses revenue + Output VAT + AR and un-recognises COGS, netting
 * every order-to-cash account to zero across the delivery, invoice and return entries.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CustomerReturnIT {

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
    private CustomerReturnService customerReturnService;
    @Autowired
    private ArSubledgerService arSubledgerService;
    @Autowired
    private LedgerReportService ledgerReportService;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
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

    @Test
    void fullReturnReversesGoodsRevenueAndCogs() {
        int n = SEQ.incrementAndGet();
        Long customerId = masterDataService.createPartner("C-RET-" + n, "Customer " + n, false, true,
                null, 30, null, null).getId();
        Long itemId = masterDataService.createItem("FG-RET-" + n, "Finished " + n, ItemType.FINISHED,
                "EA", true, new BigDecimal("10"), null, null).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal("50"),
                new BigDecimal("10"), "seed stock", JUNE, "tester");

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

        CustomerReturn ret = customerReturnService.postReturn(invoice.getId(), stockLocationId(),
                JUNE, "tester");
        Long returnStockJeId = ret.getLines().get(0).getStockJournalEntryId();
        Long creditNoteJeId = ret.getCreditNoteJournalEntryId();

        // Credit note reverses revenue / Output VAT / AR and un-recognises COGS.
        assertThat(debitFor(creditNoteJeId, "4100")).isEqualByComparingTo("600");
        assertThat(debitFor(creditNoteJeId, "2400")).isEqualByComparingTo("30");
        assertThat(creditFor(creditNoteJeId, "1200")).isEqualByComparingTo("630");
        assertThat(debitFor(creditNoteJeId, "1340")).isEqualByComparingTo("300");
        assertThat(creditFor(creditNoteJeId, "5100")).isEqualByComparingTo("300");

        // Stock returned at delivery cost: back to 50 @ 10.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("50");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("500");

        // Every order-to-cash account nets to zero across the four entries.
        Long[] jes = {deliveryJeId, invoiceJeId, returnStockJeId, creditNoteJeId};
        for (String code : List.of("1330", "1340", "5100", "4100", "2400", "1200")) {
            assertThat(netAcross(code, jes)).as("account %s nets to zero", code)
                    .isEqualByComparingTo("0");
        }

        // Invoice left the receivable cycle; books balanced; AR subledger == GL 1200.
        SalesInvoice reloaded = salesInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("RETURNED");
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

    private BigDecimal netAcross(String accountCode, Long[] journalEntryIds) {
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

    private BigDecimal glBalance(String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit - jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id "
                        + "WHERE a.code = ? AND je.status = 'POSTED'",
                BigDecimal.class, accountCode);
    }
}
