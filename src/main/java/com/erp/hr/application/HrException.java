package com.erp.hr.application;

/** Base type for HR rule violations. Mapped to problem responses by the web layer. */
public abstract class HrException extends RuntimeException {

    protected HrException(String message) {
        super(message);
    }
}
