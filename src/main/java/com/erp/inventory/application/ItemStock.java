package com.erp.inventory.application;

import java.math.BigDecimal;

/** On-hand snapshot for one item: quantity, moving-average unit cost and total value. */
public record ItemStock(
        Long itemId,
        String sku,
        BigDecimal onHandQty,
        BigDecimal avgUnitCost,
        BigDecimal totalValue) {
}
