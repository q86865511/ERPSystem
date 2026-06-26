package com.erp.inventory.api;

/**
 * The inventory module's published posting port. Other modules (purchasing, sales, manufacturing) run
 * stock movements — receipts, issues, transfers — through this interface, which updates the moving
 * average, appends the immutable subledger legs and posts the balanced journal entry in one
 * transaction. Phase 1 drives it from stock adjustments.
 */
public interface StockPosting {

    StockMovementResult post(StockMovementCommand command, String actor);

    /**
     * Adjusts an item's inventory value (and moving average) by {@code valueDelta} without changing
     * quantity — used for a purchase price variance. Appends a zero-quantity STOCK leg (so the subledger
     * stays equal to the cached value); the caller posts the matching GL line on its own journal entry.
     * Call this <em>before</em> posting that entry so the cost-state lock is taken before the journal
     * sequence — the same order as a receipt — avoiding deadlock. The item must already hold stock.
     */
    void revalue(Long itemId, java.math.BigDecimal valueDelta, String sourceDocType, String sourceDocId);
}
