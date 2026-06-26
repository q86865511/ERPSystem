package com.erp.masterdata.application;

/**
 * Raised when no inventory posting rule maps a given item type / movement type to a ledger account —
 * a configuration gap that blocks the post. Maps to HTTP 422.
 */
public class PostingRuleNotFoundException extends MasterDataException {

    public PostingRuleNotFoundException(String key) {
        super("no inventory posting rule for " + key);
    }
}
