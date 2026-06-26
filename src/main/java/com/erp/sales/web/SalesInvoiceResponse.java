package com.erp.sales.web;

import com.erp.sales.domain.InvoiceLine;
import com.erp.sales.domain.SalesInvoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API view of a customer invoice and its lines. */
public record SalesInvoiceResponse(
        Long id,
        String invoiceNumber,
        Long salesOrderId,
        Long partnerId,
        LocalDate postingDate,
        BigDecimal goodsAmount,
        BigDecimal vatAmount,
        BigDecimal cogsAmount,
        BigDecimal grossAmount,
        BigDecimal amountReceived,
        BigDecimal openBalance,
        String status,
        Long journalEntryId,
        Instant postedAt,
        List<InvoiceLineView> lines) {

    public record InvoiceLineView(
            Long id,
            Long soLineId,
            Long itemId,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal lineNet,
            BigDecimal lineVat,
            BigDecimal lineCogs) {
    }

    public static SalesInvoiceResponse from(SalesInvoice invoice) {
        List<InvoiceLineView> lines = invoice.getLines().stream().map(SalesInvoiceResponse::toView).toList();
        return new SalesInvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getSalesOrderId(), invoice.getPartnerId(), invoice.getPostingDate(),
                invoice.getGoodsAmount(), invoice.getVatAmount(), invoice.getCogsAmount(),
                invoice.getGrossAmount(), invoice.getAmountReceived(), invoice.openBalance(),
                invoice.getStatus().name(), invoice.getJournalEntryId(), invoice.getPostedAt(), lines);
    }

    private static InvoiceLineView toView(InvoiceLine line) {
        return new InvoiceLineView(line.getId(), line.getSoLineId(), line.getItemId(), line.getQty(),
                line.getUnitPrice(), line.getLineNet(), line.getLineVat(), line.getLineCogs());
    }
}
