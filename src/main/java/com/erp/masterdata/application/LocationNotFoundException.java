package com.erp.masterdata.application;

/** Raised when a location is referenced by an id that does not exist. */
public class LocationNotFoundException extends MasterDataException {

    public LocationNotFoundException(Long id) {
        super("no location with id " + id);
    }
}
