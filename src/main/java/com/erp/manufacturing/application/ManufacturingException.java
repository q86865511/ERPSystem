package com.erp.manufacturing.application;

/** Base type for manufacturing rule violations. Maps to HTTP 422 at the web layer by default. */
public abstract class ManufacturingException extends RuntimeException {

    protected ManufacturingException(String message) {
        super(message);
    }
}
