package com.erp.inventory.application;

/** Raised when a stock movement request is structurally invalid (unknown item/location, bad quantity). */
public class StockMovementValidationException extends InventoryException {

    public StockMovementValidationException(String message) {
        super(message);
    }
}
