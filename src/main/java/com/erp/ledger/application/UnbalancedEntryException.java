package com.erp.ledger.application;

import java.math.BigDecimal;

/** Raised when a journal entry's debits do not equal its credits (or total zero). */
public class UnbalancedEntryException extends LedgerException {

    public UnbalancedEntryException(BigDecimal totalDebit, BigDecimal totalCredit) {
        super("journal entry is unbalanced: debit=" + totalDebit.toPlainString()
                + " credit=" + totalCredit.toPlainString());
    }
}
