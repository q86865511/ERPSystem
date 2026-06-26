package com.erp.purchasing.application;

/** Raised when a purchase order is referenced by an id that does not exist. */
public class PurchaseOrderNotFoundException extends PurchasingException {

    public PurchaseOrderNotFoundException(Long id) {
        super("no purchase order with id " + id);
    }
}
