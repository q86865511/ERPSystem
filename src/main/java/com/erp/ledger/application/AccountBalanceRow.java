package com.erp.ledger.application;

import java.math.BigDecimal;

/** Read projection: one account's posted debit/credit totals as of a date. */
public interface AccountBalanceRow {

    String getCode();

    String getName();

    String getAccountClass();

    String getNormalBalance();

    BigDecimal getTotalDebit();

    BigDecimal getTotalCredit();
}
