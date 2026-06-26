package com.erp.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Balance sheet as of a date. Equity includes current-period earnings (retained earnings are computed
 * dynamically as revenue − expenses; the MVP does no year-end close). {@code balanced} asserts the
 * accounting equation: assets = liabilities + equity.
 */
public record BalanceSheet(
        LocalDate asOf,
        List<StatementLine> assets,
        List<StatementLine> liabilities,
        List<StatementLine> equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal netIncome,
        boolean balanced) {
}
