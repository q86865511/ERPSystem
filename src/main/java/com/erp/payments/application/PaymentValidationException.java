package com.erp.payments.application;

/** Raised when a payment request is invalid (bad amount, allocations don't sum, unknown/foreign bill). */
public class PaymentValidationException extends PaymentException {

    public PaymentValidationException(String message) {
        super(message);
    }
}
