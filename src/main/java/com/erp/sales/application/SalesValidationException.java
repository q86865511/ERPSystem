package com.erp.sales.application;

/** Raised when a sales request is structurally invalid (bad quantity, over-delivery, bad state). */
public class SalesValidationException extends SalesException {

    public SalesValidationException(String message) {
        super(message);
    }
}
