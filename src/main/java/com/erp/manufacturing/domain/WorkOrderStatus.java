package com.erp.manufacturing.domain;

/**
 * Lifecycle of a work order. DRAFT (created) → RELEASED (BOM snapshotted) → IN_PROGRESS (components
 * issued to WIP) → DONE (finished goods received). A released/in-progress order may be CANCELLED, which
 * reverses any issued components.
 */
public enum WorkOrderStatus {
    DRAFT,
    RELEASED,
    IN_PROGRESS,
    DONE,
    CANCELLED
}
