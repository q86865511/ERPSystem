package com.erp.ledger.application;

import java.math.BigDecimal;

/** One account row of a trial balance. */
public record TrialBalanceLine(
        String code,
        String name,
        String accountClass,
        BigDecimal totalDebit,
        BigDecimal totalCredit) {
}
