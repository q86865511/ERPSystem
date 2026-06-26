package com.erp.masterdata.api;

import java.math.BigDecimal;

/**
 * Published read view of an item — what other modules (inventory, purchasing, ...) need without
 * importing the {@code Item} domain entity: its id, identity, type (for account resolution), whether
 * it is stocked, its standard cost (the default unit cost for a stock gain), and the optional
 * reorder point / quantity used by the manufacturing reorder report.
 */
public record ItemView(
        Long id,
        String sku,
        String name,
        ItemType itemType,
        String uom,
        boolean stocked,
        BigDecimal standardCost,
        BigDecimal reorderPoint,
        BigDecimal reorderQty) {
}
