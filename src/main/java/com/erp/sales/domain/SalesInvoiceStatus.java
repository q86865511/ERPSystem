package com.erp.sales.domain;

/**
 * Lifecycle of a customer invoice. Receipts advance it from POSTED to PARTIALLY_PAID / PAID; a full
 * credit note on an unpaid invoice moves it to RETURNED (out of the receivable cycle).
 */
public enum SalesInvoiceStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    RETURNED
}
