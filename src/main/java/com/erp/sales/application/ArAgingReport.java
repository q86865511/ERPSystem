package com.erp.sales.application;

import java.math.BigDecimal;

/**
 * Accounts-receivable aging: open customer-invoice balances bucketed by how long they have been due, as
 * of a date. {@code current} is not yet due; the rest are days past due.
 */
public record ArAgingReport(
        BigDecimal current,
        BigDecimal days1to30,
        BigDecimal days31to60,
        BigDecimal days61to90,
        BigDecimal days90plus,
        BigDecimal total) {
}
