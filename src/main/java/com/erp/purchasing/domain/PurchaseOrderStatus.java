package com.erp.purchasing.domain;

/** Lifecycle of a purchase order. A PO is a commitment and posts nothing; receipts drive its status. */
public enum PurchaseOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CLOSED
}
