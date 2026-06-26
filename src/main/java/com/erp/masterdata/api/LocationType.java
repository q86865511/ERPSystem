package com.erp.masterdata.api;

/**
 * The role a location plays. STOCK holds real on-hand inventory and feeds moving-average cost; the
 * others are typed virtual locations so every stock movement is a balanced transfer between two
 * locations (e.g. an adjustment moves STOCK ↔ INVENTORY_LOSS, a receipt VENDOR → STOCK).
 */
public enum LocationType {
    STOCK,
    RECEIVING,
    SHIPPING,
    PRODUCTION_WIP,
    SCRAP,
    VENDOR,
    CUSTOMER,
    INVENTORY_LOSS
}
