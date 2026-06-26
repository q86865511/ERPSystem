package com.erp.sales.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.api.StockPosting;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.sales.domain.CustomerReturn;
import com.erp.sales.domain.InvoiceLine;
import com.erp.sales.domain.SalesInvoice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Posts customer returns (credit notes) for a posted, unpaid invoice — a full reversal. Each invoiced
 * line drives a stock return (CUSTOMER → STOCK at the delivery cost) through {@link StockPosting}, which
 * posts {@code Dr Finished Goods / Cr Deferred-COGS}; a single credit note then reverses revenue, Output
 * VAT and AR, and un-recognises COGS ({@code Dr Deferred-COGS / Cr COGS}). The invoice moves to RETURNED.
 * Everything commits in one transaction; nothing posted earlier is mutated (append-only reversal).
 */
@Service
public class CustomerReturnService {

    private static final String SEQUENCE_SCOPE = "CUSTOMER_RETURN";
    private static final String SOURCE_DOC_TYPE = "CUSTOMER_RETURN";
    private static final String AR_ACCOUNT = "1200";
    private static final String REVENUE_ACCOUNT = "4100";
    private static final String OUTPUT_VAT_ACCOUNT = "2400";
    private static final String COGS_ACCOUNT = "5100";
    private static final String DEFERRED_COGS_ACCOUNT = "1340";
    private static final int MONEY_SCALE = 4;
    private static final int COST_SCALE = 6;

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final CustomerReturnRepository customerReturnRepository;
    private final SequenceAllocator sequenceAllocator;
    private final LedgerPosting ledgerPosting;
    private final StockPosting stockPosting;
    private final MasterDataQuery masterDataQuery;

    public CustomerReturnService(SalesInvoiceRepository salesInvoiceRepository,
                                 CustomerReturnRepository customerReturnRepository,
                                 SequenceAllocator sequenceAllocator,
                                 LedgerPosting ledgerPosting,
                                 StockPosting stockPosting,
                                 MasterDataQuery masterDataQuery) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.customerReturnRepository = customerReturnRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.ledgerPosting = ledgerPosting;
        this.stockPosting = stockPosting;
        this.masterDataQuery = masterDataQuery;
    }

    @Transactional
    public CustomerReturn postReturn(Long invoiceId, Long stockLocationId, LocalDate postingDate,
                                     String actor) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new SalesInvoiceNotFoundException(invoiceId));

        LocationView stockLocation = masterDataQuery.findLocation(stockLocationId)
                .orElseThrow(() -> new SalesValidationException("unknown location " + stockLocationId));
        if (stockLocation.type() != LocationType.STOCK) {
            throw new SalesValidationException(
                    "return target must be a STOCK location, was " + stockLocation.type());
        }
        LocationView customerLocation = masterDataQuery
                .findLocationByType(stockLocation.warehouseId(), LocationType.CUSTOMER)
                .orElseThrow(() -> new SalesValidationException(
                        "no CUSTOMER location in warehouse " + stockLocation.warehouseId()));

        String returnNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        CustomerReturn customerReturn = new CustomerReturn(returnNumber, invoice.getId(),
                invoice.getSalesOrderId(), invoice.getPartnerId(), postingDate);

        BigDecimal totalNet = zero();
        BigDecimal totalVat = zero();
        BigDecimal totalCogs = zero();

        int lineNo = 0;
        for (InvoiceLine line : invoice.getLines()) {
            lineNo++;
            BigDecimal qty = line.getQty();
            // Return goods to stock at the cost they were invoiced out (cogs / qty).
            BigDecimal unitCost = line.getLineCogs().divide(qty, COST_SCALE, RoundingMode.HALF_UP);
            StockMovementCommand command = new StockMovementCommand(
                    line.getItemId(), customerLocation.id(), stockLocationId, qty, unitCost,
                    InventoryMovementType.SALES_RETURN, SOURCE_DOC_TYPE, returnNumber + "#" + lineNo,
                    postingDate, "customer return " + returnNumber);
            StockMovementResult result = stockPosting.post(command, actor);

            totalNet = totalNet.add(line.getLineNet());
            totalVat = totalVat.add(line.getLineVat());
            totalCogs = totalCogs.add(result.value());
            customerReturn.addLine(line.getSoLineId(), line.getItemId(), qty, line.getLineNet(),
                    line.getLineVat(), result.value(), result.movementGroupId(), result.journalEntryId());
        }

        BigDecimal gross = totalNet.add(totalVat);

        // Credit note: reverse revenue/AR, and un-recognise COGS by clearing back into Deferred-COGS.
        List<Line> entryLines = new ArrayList<>();
        entryLines.add(new Line(REVENUE_ACCOUNT, totalNet, null, "sales return revenue reversal", null));
        if (totalVat.signum() > 0) {
            entryLines.add(new Line(OUTPUT_VAT_ACCOUNT, totalVat, null, "output VAT reversal", null));
        }
        entryLines.add(new Line(AR_ACCOUNT, null, gross, "accounts receivable reversal",
                invoice.getPartnerId()));
        if (totalCogs.signum() > 0) {
            entryLines.add(new Line(DEFERRED_COGS_ACCOUNT, totalCogs, null, "deferred COGS clearing",
                    invoice.getPartnerId()));
            entryLines.add(new Line(COGS_ACCOUNT, null, totalCogs, "COGS reversal", null));
        }

        JournalEntryRequest request = new JournalEntryRequest(null, postingDate,
                "credit note " + returnNumber, null, SOURCE_DOC_TYPE, returnNumber, "CREDIT_NOTE",
                entryLines);
        PostingResult posting = ledgerPosting.post(request, actor);

        customerReturn.finalise(totalNet, totalVat, totalCogs, gross, posting.entryId());
        invoice.markReturned();
        salesInvoiceRepository.save(invoice);
        return customerReturnRepository.saveAndFlush(customerReturn);
    }

    @Transactional(readOnly = true)
    public CustomerReturn getReturn(Long id) {
        return customerReturnRepository.findById(id)
                .orElseThrow(() -> new CustomerReturnNotFoundException(id));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
