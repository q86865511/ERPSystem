package com.erp.inventory.api;

import com.erp.masterdata.api.InventoryMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A request to move stock from one location to another as a balanced two-leg movement. {@code qty} is
 * a positive magnitude. {@code unitCost} is used when the destination is a STOCK location receiving at a
 * known cost (a receipt/gain) — null falls back to the item's standard cost; when stock leaves a STOCK
 * location it is valued at the current moving average and {@code unitCost} is ignored. The
 * {@code sourceDocType}/{@code sourceDocId} plus the movement type make the ledger post idempotent.
 */
public record StockMovementCommand(
        Long itemId,
        Long fromLocationId,
        Long toLocationId,
        BigDecimal qty,
        BigDecimal unitCost,
        InventoryMovementType movementType,
        String sourceDocType,
        String sourceDocId,
        LocalDate postingDate,
        String memo) {
}
