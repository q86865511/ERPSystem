package com.erp.sales.application;

/** Raised when a customer invoice is referenced by an id that does not exist. */
public class SalesInvoiceNotFoundException extends SalesException {

    public SalesInvoiceNotFoundException(Long id) {
        super("no sales invoice with id " + id);
    }
}
