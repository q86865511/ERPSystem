package com.erp.masterdata.api;

/**
 * The kind of stock movement. Phase 1 uses ADJUSTMENT_IN/ADJUSTMENT_OUT; RECEIPT clears against GR-IR
 * (procure-to-pay); SHIPMENT/SALES_RETURN clear against Deferred-COGS (order-to-cash); the
 * MANUFACTURING_* types clear against WIP (manufacturing). Each type maps to exactly one COUNTER posting
 * rule, so the inventory leg's offset account is resolved by type.
 */
public enum InventoryMovementType {
    RECEIPT,
    ISSUE,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    TRANSFER,
    SHIPMENT,
    SALES_RETURN,
    MANUFACTURING_ISSUE,
    MANUFACTURING_RECEIPT,
    MANUFACTURING_RETURN
}
