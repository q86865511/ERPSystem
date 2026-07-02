package com.erp.inventory.application;

import com.erp.masterdata.api.ItemType;

import java.math.BigDecimal;

/**
 * Per-item stock status for the inventory heat treemap: on-hand value plus a health flag — {@code OUT}
 * (nothing on hand), {@code LOW} (at or below the reorder point) or {@code OK}.
 */
public record ItemStatus(Long itemId, String sku, String name, ItemType itemType, BigDecimal value,
                         BigDecimal onHandQty, BigDecimal reorderPoint, String status) {
}
