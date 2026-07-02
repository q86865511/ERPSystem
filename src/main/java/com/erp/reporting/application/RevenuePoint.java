package com.erp.reporting.application;

import java.math.BigDecimal;

/** One month on the revenue trend: revenue, cost of goods sold, and the resulting gross margin. */
public record RevenuePoint(String month, BigDecimal revenue, BigDecimal cogs, BigDecimal grossMargin) {
}
