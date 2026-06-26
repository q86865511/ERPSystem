package com.erp.masterdata.application;

/** Raised when creating a partner whose code is already taken. */
public class DuplicatePartnerCodeException extends MasterDataException {

    public DuplicatePartnerCodeException(String code) {
        super("a partner with code " + code + " already exists");
    }
}
