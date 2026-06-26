package com.erp.purchasing.application;

/** Raised when a purchasing request is structurally invalid (bad quantity, over-receipt, bad state). */
public class PurchasingValidationException extends PurchasingException {

    public PurchasingValidationException(String message) {
        super(message);
    }
}
