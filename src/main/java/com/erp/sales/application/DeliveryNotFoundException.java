package com.erp.sales.application;

/** Raised when a delivery is referenced by an id that does not exist. */
public class DeliveryNotFoundException extends SalesException {

    public DeliveryNotFoundException(Long id) {
        super("no delivery with id " + id);
    }
}
