package com.erp.inventory.api;

import java.math.BigDecimal;

/** Published view of an item's current stock position from the moving-average cost cache. */
public record ItemOnHand(
        Long itemId,
        BigDecimal onHandQty,
        BigDecimal avgUnitCost,
        BigDecimal totalValue) {
}
