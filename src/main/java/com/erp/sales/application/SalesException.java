package com.erp.sales.application;

/** Base type for sales rule violations. Maps to HTTP 422 at the web layer by default. */
public abstract class SalesException extends RuntimeException {

    protected SalesException(String message) {
        super(message);
    }
}
