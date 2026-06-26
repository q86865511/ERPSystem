package com.erp.masterdata.application;

/** Base type for master-data rule violations. Maps to HTTP 422 at the web layer by default. */
public abstract class MasterDataException extends RuntimeException {

    protected MasterDataException(String message) {
        super(message);
    }
}
