package com.erp.ledger.application;

/**
 * Raised when an entry cannot be reversed: not POSTED, document-sourced (must be reversed via its owning
 * subledger module to keep subledger == GL), or already reversed. Maps to HTTP 422.
 */
public class EntryNotReversibleException extends LedgerException {

    public EntryNotReversibleException(String message) {
        super(message);
    }
}
