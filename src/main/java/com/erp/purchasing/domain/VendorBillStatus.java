package com.erp.purchasing.domain;

/** Lifecycle of a vendor bill. Created POSTED; payment allocation drives it to PARTIALLY_PAID / PAID. */
public enum VendorBillStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID
}
