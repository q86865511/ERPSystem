package com.erp.manufacturing.application;

/** Raised when a work order is referenced by an id that does not exist. */
public class WorkOrderNotFoundException extends ManufacturingException {

    public WorkOrderNotFoundException(Long id) {
        super("no work order with id " + id);
    }
}
