package com.erp.purchasing.application;

/** Raised when a vendor bill is referenced by an id that does not exist. */
public class VendorBillNotFoundException extends PurchasingException {

    public VendorBillNotFoundException(Long id) {
        super("no vendor bill with id " + id);
    }
}
