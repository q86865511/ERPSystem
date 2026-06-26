package com.erp.sales.domain;

/** Lifecycle of a customer invoice. Receipts advance it from POSTED to PARTIALLY_PAID / PAID. */
public enum SalesInvoiceStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID
}
