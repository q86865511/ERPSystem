package com.erp.inventory.application;

import java.math.BigDecimal;

/**
 * Raised when an issue/loss would drive an item's on-hand quantity negative. The MVP blocks this —
 * moving-average cost is meaningless under negative stock.
 */
public class NegativeInventoryException extends InventoryException {

    public NegativeInventoryException(Long itemId, BigDecimal onHand, BigDecimal requested) {
        super("cannot issue " + requested.toPlainString() + " of item " + itemId
                + " with only " + onHand.toPlainString() + " on hand");
    }
}
