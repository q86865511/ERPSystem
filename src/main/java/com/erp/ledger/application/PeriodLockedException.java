package com.erp.ledger.application;

/** Raised when an operation (reopen / soft-close) targets a period that has been hard-closed (LOCKED). */
public class PeriodLockedException extends LedgerException {

    public PeriodLockedException(String yearCode, int periodNo) {
        super("fiscal period " + yearCode + "/" + periodNo + " is locked by a year-end close");
    }
}
