package com.erp.ledger.application;

import java.math.BigDecimal;

/** Read projection: one account's posted debit/credit totals for the trial balance. */
public interface TrialBalanceRow {

    String getCode();

    String getName();

    String getAccountClass();

    BigDecimal getTotalDebit();

    BigDecimal getTotalCredit();
}
