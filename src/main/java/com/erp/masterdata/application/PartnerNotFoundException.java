package com.erp.masterdata.application;

/** Raised when a partner is referenced by an id or code that does not exist. */
public class PartnerNotFoundException extends MasterDataException {

    public PartnerNotFoundException(String reference) {
        super("no partner " + reference);
    }
}
