package com.erp.ledger.api;

import java.time.LocalDate;
import java.util.List;

/**
 * The ledger module's published read port over posted balances. The reporting module builds the trial
 * balance, balance sheet and income statement from this contract, never touching ledger internals.
 */
public interface GeneralLedgerQuery {

    /** Posted account balances for entries with posting date on or before {@code asOf}. */
    List<AccountBalance> accountBalances(LocalDate asOf);

    /** Posted journal lines for one account on or before {@code asOf} (drill-down), oldest first. */
    List<LedgerLineView> linesForAccount(String accountCode, LocalDate asOf);
}
