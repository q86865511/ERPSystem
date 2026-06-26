package com.erp.manufacturing.application;

/** Raised when a bill of materials is referenced by an id that does not exist. */
public class BomNotFoundException extends ManufacturingException {

    public BomNotFoundException(Long id) {
        super("no bill of materials with id " + id);
    }
}
