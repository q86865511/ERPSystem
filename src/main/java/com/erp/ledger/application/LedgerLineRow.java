package com.erp.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read projection: one posted journal line for general-ledger drill-down. */
public interface LedgerLineRow {

    Long getEntryId();

    Long getEntryNo();

    LocalDate getPostingDate();

    String getAccountCode();

    BigDecimal getDebit();

    BigDecimal getCredit();

    String getMemo();

    String getSourceDocType();

    String getSourceDocId();
}
