package com.erp.ledger.application;

/** Raised when no journal entry exists for the requested entry number. Maps to HTTP 404. */
public class EntryNotFoundException extends LedgerException {

    public EntryNotFoundException(Long entryNo) {
        super("no journal entry with number " + entryNo);
    }
}
