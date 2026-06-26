package com.erp.sales.api;

import java.math.BigDecimal;

/** Published view of a receivable customer invoice — what the payments module needs to allocate against it. */
public record ReceivableInvoiceView(
        Long id,
        String invoiceNumber,
        Long partnerId,
        BigDecimal grossAmount,
        BigDecimal amountReceived,
        BigDecimal openBalance,
        String status) {
}
