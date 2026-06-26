package com.erp.purchasing.application;

/** Base type for purchasing rule violations. Maps to HTTP 422 at the web layer by default. */
public abstract class PurchasingException extends RuntimeException {

    protected PurchasingException(String message) {
        super(message);
    }
}
