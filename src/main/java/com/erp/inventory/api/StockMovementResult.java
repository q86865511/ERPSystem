package com.erp.inventory.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The outcome of a posted stock movement: the movement group linking its two legs, the journal entry
 * it posted, and the item's new on-hand quantity and average unit cost.
 */
public record StockMovementResult(
        UUID movementGroupId,
        Long journalEntryId,
        Long journalEntryNo,
        BigDecimal newOnHandQty,
        BigDecimal newAvgUnitCost) {
}
