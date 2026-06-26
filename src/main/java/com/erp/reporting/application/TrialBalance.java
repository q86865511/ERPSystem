package com.erp.reporting.application;

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

    public record Line(String code, String name, String accountClass, BigDecimal debit,
                       BigDecimal credit) {
    }
}
