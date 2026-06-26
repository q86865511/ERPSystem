package com.erp.payments.application;

/** Base type for payment rule violations. Maps to HTTP 422 at the web layer by default. */
public abstract class PaymentException extends RuntimeException {

    protected PaymentException(String message) {
        super(message);
    }
}
