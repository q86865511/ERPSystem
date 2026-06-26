package com.erp.inventory.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The outcome of a posted stock movement: the movement group linking its two legs, the journal entry it
 * posted, the item's new on-hand quantity and average unit cost, and the cost the movement itself posted
 * at — {@code unitCost} (per unit) and {@code value} (the leg value, money scale). For an issue the unit
 * cost is the moving average <em>at the time of the issue</em> (callers must use this, not
 * {@code newAvgUnitCost}, which is 0 after a full drain) — e.g. the delivery cost to defer, or the
 * component cost to roll into a work order.
 */
public record StockMovementResult(
        UUID movementGroupId,
        Long journalEntryId,
        Long journalEntryNo,
        BigDecimal newOnHandQty,
        BigDecimal newAvgUnitCost,
        BigDecimal unitCost,
        BigDecimal value) {
}
