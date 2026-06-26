package com.erp.manufacturing.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reorder-point report: items whose on-hand quantity has fallen below their reorder point, with the
 * suggested reorder quantity. Drives replenishment planning.
 */
public record ReorderReport(List<ReorderItem> items) {

    public record ReorderItem(
            Long itemId,
            String sku,
            String name,
            BigDecimal onHandQty,
            BigDecimal reorderPoint,
            BigDecimal reorderQty) {
    }
}
