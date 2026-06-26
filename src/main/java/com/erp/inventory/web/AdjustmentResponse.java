package com.erp.inventory.web;

import com.erp.inventory.domain.StockAdjustment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** API view of a posted stock adjustment. */
public record AdjustmentResponse(
        Long id,
        String adjustmentNumber,
        Long itemId,
        Long locationId,
        BigDecimal qtyDelta,
        BigDecimal unitCost,
        String reason,
        String status,
        UUID movementGroupId,
        Long journalEntryId,
        Instant postedAt) {

    public static AdjustmentResponse from(StockAdjustment adjustment) {
        return new AdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNumber(),
                adjustment.getItemId(),
                adjustment.getLocationId(),
                adjustment.getQtyDelta(),
                adjustment.getUnitCost(),
                adjustment.getReason(),
                adjustment.getStatus().name(),
                adjustment.getMovementGroupId(),
                adjustment.getJournalEntryId(),
                adjustment.getPostedAt());
    }
}
