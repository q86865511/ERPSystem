package com.erp.reporting.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Headline KPIs for the current month against the previous month, each with a period-over-period delta.
 */
public record KpiSummary(String currentMonth, String previousMonth, Metric revenue, Metric grossProfit,
                         Metric netCash) {

    /** One KPI: its current value, the prior-period value, and the percentage change between them. */
    @Schema(name = "KpiMetric")
    public record Metric(BigDecimal current, BigDecimal previous, BigDecimal deltaPct) {
    }
}
