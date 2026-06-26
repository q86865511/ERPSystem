package com.erp.ledger.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * A trial balance: per-account posted totals plus the grand totals. {@code balanced} is the headline
 * correctness fact — in a sound double-entry ledger {@code totalDebit} always equals {@code totalCredit}.
 */
public record TrialBalanceReport(
        List<TrialBalanceLine> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced) {
}
