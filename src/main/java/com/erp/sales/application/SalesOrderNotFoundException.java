package com.erp.sales.application;

/** Raised when a sales order is referenced by an id that does not exist. */
public class SalesOrderNotFoundException extends SalesException {

    public SalesOrderNotFoundException(Long id) {
        super("no sales order with id " + id);
    }
}
