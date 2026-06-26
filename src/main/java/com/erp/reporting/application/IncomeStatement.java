package com.erp.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Income statement as of a date: revenue less expenses gives net income. */
public record IncomeStatement(
        LocalDate asOf,
        List<StatementLine> revenue,
        List<StatementLine> expenses,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome) {
}
