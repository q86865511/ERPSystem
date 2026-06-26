package com.erp.ledger.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Published view of one posted journal line for an account (general-ledger drill-down). */
public record LedgerLineView(
        Long entryId,
        Long entryNo,
        LocalDate postingDate,
        String accountCode,
        BigDecimal debit,
        BigDecimal credit,
        String memo,
        String sourceDocType,
        String sourceDocId) {
}
