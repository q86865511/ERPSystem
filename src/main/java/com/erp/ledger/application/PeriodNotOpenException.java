package com.erp.ledger.application;

import java.time.LocalDate;

/** Raised when there is no OPEN fiscal period for the requested posting date. */
public class PeriodNotOpenException extends LedgerException {

    public PeriodNotOpenException(LocalDate postingDate) {
        super("no open fiscal period for posting date " + postingDate);
    }
}
