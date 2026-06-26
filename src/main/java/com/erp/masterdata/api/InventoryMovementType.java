package com.erp.masterdata.api;

/**
 * The kind of stock movement. Phase 1 uses ADJUSTMENT_IN/ADJUSTMENT_OUT; RECEIPT/ISSUE/TRANSFER are
 * reserved for the procure-to-pay, order-to-cash and manufacturing phases that reuse the same
 * posting machinery.
 */
public enum InventoryMovementType {
    RECEIPT,
    ISSUE,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    TRANSFER
}
