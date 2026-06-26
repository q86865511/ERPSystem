package com.erp.sales.application;

/** Raised when a customer return is referenced by an id that does not exist. */
public class CustomerReturnNotFoundException extends SalesException {

    public CustomerReturnNotFoundException(Long id) {
        super("no customer return with id " + id);
    }
}
