package com.erp.inventory.application;

/** Base type for inventory rule violations. Maps to HTTP 422 at the web layer by default. */
public abstract class InventoryException extends RuntimeException {

    protected InventoryException(String message) {
        super(message);
    }
}
