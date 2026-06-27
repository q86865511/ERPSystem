package com.erp.reporting.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Trial balance as of a date: posted debit/credit totals per account, which must net to zero. */
public record TrialBalance(
        LocalDate asOf,
        List<Line> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced) {

    // Explicit schema name: distinct from JournalEntryRequest.Line, which otherwise collapses to the
    // same "Line" schema in the OpenAPI spec and mistypes these rows.
    @Schema(name = "TrialBalanceRow")
    public record Line(String code, String name, String accountClass, BigDecimal debit,
                       BigDecimal credit) {
    }
}
