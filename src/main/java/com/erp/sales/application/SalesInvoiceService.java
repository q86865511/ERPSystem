package com.erp.sales.application;

import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.sales.api.ReceivableDocuments;
import com.erp.sales.api.ReceivableInvoiceView;
import com.erp.sales.domain.DeliveryLine;
import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesOrder;
import com.erp.sales.domain.SoLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Posts customer invoices. An invoice recognises revenue + Output VAT against the AR control account at
 * the sales price, and recognises COGS by matching its lines (FIFO) to delivered-but-uninvoiced delivery
 * lines and clearing their Deferred-COGS at the delivery cost. AR and Deferred-COGS lines are tagged with
 * the customer. Everything commits in one transaction. Also serves the {@link ReceivableDocuments} port.
 */
@Service
public class SalesInvoiceService implements ReceivableDocuments {

    private static final String SEQUENCE_SCOPE = "SALES_INVOICE";
    private static final String SOURCE_DOC_TYPE = "SALES_INVOICE";
    private static final String AR_ACCOUNT = "1200";
    private static final String REVENUE_ACCOUNT = "4100";
    private static final String OUTPUT_VAT_ACCOUNT = "2400";
    private static final String COGS_ACCOUNT = "5100";
    private static final String DEFERRED_COGS_ACCOUNT = "1340";
    private static final int MONEY_SCALE = 4;

    private final SalesOrderRepository salesOrderRepository;
    private final DeliveryLineRepository deliveryLineRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SequenceAllocator sequenceAllocator;
    private final LedgerPosting ledgerPosting;
    private final MasterDataQuery masterDataQuery;

    public SalesInvoiceService(SalesOrderRepository salesOrderRepository,
                               DeliveryLineRepository deliveryLineRepository,
                               SalesInvoiceRepository salesInvoiceRepository,
                               SequenceAllocator sequenceAllocator,
                               LedgerPosting ledgerPosting,
                               MasterDataQuery masterDataQuery) {
        this.salesOrderRepository = salesOrderRepository;
        this.deliveryLineRepository = deliveryLineRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.ledgerPosting = ledgerPosting;
        this.masterDataQuery = masterDataQuery;
    }

    public record InvoiceLineInput(Long soLineId, BigDecimal qty, BigDecimal unitPrice) {
    }

    @Transactional
    public SalesInvoice postInvoice(Long soId, List<InvoiceLineInput> invoiceLines, String taxRateCode,
                                    LocalDate postingDate, String actor) {
        if (invoiceLines == null || invoiceLines.isEmpty()) {
            throw new SalesValidationException("a customer invoice needs at least one line");
        }
        SalesOrder order = salesOrderRepository.findById(soId)
                .orElseThrow(() -> new SalesOrderNotFoundException(soId));
        BigDecimal rate = masterDataQuery.findTaxRate(taxRateCode)
                .orElseThrow(() -> new SalesValidationException("unknown tax rate " + taxRateCode));

        String invoiceNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        SalesInvoice invoice = new SalesInvoice(invoiceNumber, soId, order.getPartnerId(), postingDate);

        BigDecimal totalNet = zero();
        BigDecimal totalVat = zero();
        BigDecimal totalCogs = zero();

        for (InvoiceLineInput input : invoiceLines) {
            SoLine soLine = order.lineById(input.soLineId());
            BigDecimal qty = input.qty();
            if (qty == null || qty.signum() <= 0) {
                throw new SalesValidationException("invoice quantity must be positive");
            }
            BigDecimal lineCogs = allocateAgainstDeliveries(input.soLineId(), qty);
            order.applyInvoicing(input.soLineId(), qty);

            BigDecimal lineNet = qty.multiply(input.unitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineNet.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            totalNet = totalNet.add(lineNet);
            totalVat = totalVat.add(lineVat);
            totalCogs = totalCogs.add(lineCogs);
            invoice.addLine(input.soLineId(), soLine.getItemId(), qty, input.unitPrice(), lineNet, lineVat,
                    lineCogs);
        }

        BigDecimal gross = totalNet.add(totalVat);

        List<Line> entryLines = new ArrayList<>();
        // Revenue side: Dr AR (gross) / Cr Revenue (net), Cr Output VAT (vat).
        entryLines.add(new Line(AR_ACCOUNT, gross, null, "accounts receivable", order.getPartnerId()));
        entryLines.add(new Line(REVENUE_ACCOUNT, null, totalNet, "sales revenue", null));
        if (totalVat.signum() > 0) {
            entryLines.add(new Line(OUTPUT_VAT_ACCOUNT, null, totalVat, "output VAT", null));
        }
        // COGS side: Dr COGS / Cr Deferred-COGS (clears what delivery parked).
        if (totalCogs.signum() > 0) {
            entryLines.add(new Line(COGS_ACCOUNT, totalCogs, null, "cost of goods sold", null));
            entryLines.add(new Line(DEFERRED_COGS_ACCOUNT, null, totalCogs, "deferred COGS clearing",
                    order.getPartnerId()));
        }

        JournalEntryRequest request = new JournalEntryRequest(null, postingDate,
                "sales invoice " + invoiceNumber, null, SOURCE_DOC_TYPE, invoiceNumber, "INVOICE",
                entryLines);
        PostingResult posting = ledgerPosting.post(request, actor);

        invoice.markPosted(totalNet, totalVat, totalCogs, gross, posting.entryId());
        salesOrderRepository.save(order);
        return salesInvoiceRepository.saveAndFlush(invoice);
    }

    /** FIFO-allocates an invoiced quantity to an SO line's open deliveries, returning the COGS cleared. */
    private BigDecimal allocateAgainstDeliveries(Long soLineId, BigDecimal qty) {
        BigDecimal remaining = qty;
        BigDecimal cogsCleared = zero();
        for (DeliveryLine deliveryLine : deliveryLineRepository.findBySoLineIdOrderByIdAsc(soLineId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal available = deliveryLine.outstandingToInvoice();
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal allocated = available.min(remaining);
            cogsCleared = cogsCleared.add(
                    allocated.multiply(deliveryLine.getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            deliveryLine.invoice(allocated);
            remaining = remaining.subtract(allocated);
        }
        if (remaining.signum() > 0) {
            throw new SalesValidationException(
                    "not enough delivered-but-uninvoiced quantity to invoice " + qty + " on so line "
                            + soLineId);
        }
        return cogsCleared;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReceivableInvoiceView> findOpenInvoice(Long invoiceId) {
        return salesInvoiceRepository.findById(invoiceId).map(SalesInvoiceService::toReceivableView);
    }

    @Override
    @Transactional
    public void applyReceipt(Long invoiceId, BigDecimal amount) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new SalesInvoiceNotFoundException(invoiceId));
        invoice.applyReceipt(amount);
        salesInvoiceRepository.saveAndFlush(invoice);
    }

    @Transactional(readOnly = true)
    public SalesInvoice getInvoice(Long id) {
        return salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new SalesInvoiceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> listInvoices() {
        return salesInvoiceRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "id"));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static ReceivableInvoiceView toReceivableView(SalesInvoice invoice) {
        return new ReceivableInvoiceView(invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getPartnerId(), invoice.getGrossAmount(), invoice.getAmountReceived(),
                invoice.openBalance(), invoice.getStatus().name());
    }
}
