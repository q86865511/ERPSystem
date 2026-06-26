package com.erp.masterdata.application;

/** Raised when an item is referenced by an id or sku that does not exist. */
public class ItemNotFoundException extends MasterDataException {

    public ItemNotFoundException(String reference) {
        super("no item " + reference);
    }
}
