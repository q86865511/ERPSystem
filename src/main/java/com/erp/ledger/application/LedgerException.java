package com.erp.ledger.application;

/** Base type for domain-rule violations raised by the ledger. Maps to HTTP 422 at the web layer. */
public abstract class LedgerException extends RuntimeException {

    protected LedgerException(String message) {
        super(message);
    }
}
