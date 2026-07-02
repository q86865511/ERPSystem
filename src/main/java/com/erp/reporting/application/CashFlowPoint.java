package com.erp.reporting.application;

import java.math.BigDecimal;

/** One month of cash movement: inflow (cash debits), outflow (cash credits) and the net change. */
public record CashFlowPoint(String month, BigDecimal inflow, BigDecimal outflow, BigDecimal net) {
}
