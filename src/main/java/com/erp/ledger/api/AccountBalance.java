package com.erp.ledger.api;

import java.math.BigDecimal;

/**
 * Published view of one account's posted debit/credit totals (up to an as-of date). {@code accountClass}
 * and {@code normalBalance} are the enum names as strings, so the contract stays free of ledger
 * internals — consumers (the reporting module) classify and sign balances from these.
 */
public record AccountBalance(
        String code,
        String name,
        String accountClass,
        String normalBalance,
        BigDecimal totalDebit,
        BigDecimal totalCredit) {

    /** The account's balance in its natural (normal-balance) sign. */
    public BigDecimal naturalBalance() {
        return "DEBIT".equals(normalBalance)
                ? totalDebit.subtract(totalCredit)
                : totalCredit.subtract(totalDebit);
    }
}
