package com.erp.masterdata.application;

/** Raised when a warehouse is referenced by an id that does not exist. */
public class WarehouseNotFoundException extends MasterDataException {

    public WarehouseNotFoundException(Long id) {
        super("no warehouse with id " + id);
    }
}
