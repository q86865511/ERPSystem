package com.erp.inventory.api;

/**
 * The inventory module's published posting port. Other modules (purchasing, sales, manufacturing) run
 * stock movements — receipts, issues, transfers — through this interface, which updates the moving
 * average, appends the immutable subledger legs and posts the balanced journal entry in one
 * transaction. Phase 1 drives it from stock adjustments.
 */
public interface StockPosting {

    StockMovementResult post(StockMovementCommand command, String actor);
}
