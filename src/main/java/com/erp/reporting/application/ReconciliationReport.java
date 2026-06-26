package com.erp.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The reconciliation health-check — the project's hero artifact. Asserts the hard invariants that prove
 * the books are correct: the trial balance balances and every subledger equals its GL control account.
 * Transitional clearing accounts (GR-IR, Deferred-COGS, WIP) are reported too — they net to zero once
 * their cycles complete. {@code healthy} is true iff the trial balance balances and all subledgers
 * reconcile.
 */
public record ReconciliationReport(
        LocalDate asOf,
        boolean healthy,
        boolean trialBalanceBalanced,
        List<SubledgerCheck> subledgerChecks,
        List<ClearingBalance> clearingAccounts) {

    /** A subledger reconciled against its GL control account. */
    public record SubledgerCheck(
            String label,
            String accountCode,
            BigDecimal subledger,
            BigDecimal glControl,
            boolean reconciled) {
    }

    /** A transitional clearing account's balance (zero once its cycle completes). */
    public record ClearingBalance(
            String accountCode,
            String name,
            BigDecimal balance,
            boolean cleared) {
    }
}
