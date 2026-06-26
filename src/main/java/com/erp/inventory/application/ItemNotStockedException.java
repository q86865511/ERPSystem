package com.erp.inventory.application;

/** Raised when a stock movement targets an item that is not stocked. */
public class ItemNotStockedException extends InventoryException {

    public ItemNotStockedException(String sku) {
        super("item " + sku + " is not stocked");
    }
}
