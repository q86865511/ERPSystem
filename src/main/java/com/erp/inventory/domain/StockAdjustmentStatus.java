package com.erp.inventory.domain;

/** Lifecycle of a stock-adjustment document. Phase 1 posts in one step (DRAFT → POSTED). */
public enum StockAdjustmentStatus {
    DRAFT,
    POSTED
}
