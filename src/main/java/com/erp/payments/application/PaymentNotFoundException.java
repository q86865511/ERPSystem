package com.erp.payments.application;

/** Raised when a payment is referenced by an id that does not exist. */
public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(Long id) {
        super("no payment with id " + id);
    }
}
