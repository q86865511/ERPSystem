package com.erp.ledger.application;

/** Raised when a year-end close is requested for a fiscal year that is already closed (idempotency guard). */
public class YearAlreadyClosedException extends LedgerException {

    public YearAlreadyClosedException(String yearCode) {
        super("fiscal year " + yearCode + " is already closed");
    }
}
