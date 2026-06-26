package com.erp.purchasing.application;

/** Raised when a goods receipt is referenced by an id that does not exist. */
public class GoodsReceiptNotFoundException extends PurchasingException {

    public GoodsReceiptNotFoundException(Long id) {
        super("no goods receipt with id " + id);
    }
}
