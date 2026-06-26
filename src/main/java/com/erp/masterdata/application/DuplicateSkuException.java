package com.erp.masterdata.application;

/** Raised when creating an item whose sku is already taken. */
public class DuplicateSkuException extends MasterDataException {

    public DuplicateSkuException(String sku) {
        super("an item with sku " + sku + " already exists");
    }
}
