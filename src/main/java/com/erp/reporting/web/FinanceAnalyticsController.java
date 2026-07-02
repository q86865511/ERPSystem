package com.erp.reporting.web;

import com.erp.reporting.application.BudgetVarianceReport;
import com.erp.reporting.application.CashFlowPoint;
import com.erp.reporting.application.FinanceAnalyticsService;
import com.erp.reporting.application.KpiSummary;
import com.erp.reporting.application.RevenuePoint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** REST surface for the finance-centre analytics charts (revenue/cash trends, KPIs, budget variance). */
@RestController
@RequestMapping("/api/reporting")
public class FinanceAnalyticsController {

    private final FinanceAnalyticsService analyticsService;

    public FinanceAnalyticsController(FinanceAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/revenue-trend")
    public List<RevenuePoint> revenueTrend(
            @RequestParam(required = false, defaultValue = "6") int months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return analyticsService.revenueTrend(months, asOfOrToday(asOf));
    }

    @GetMapping("/cash-flow")
    public List<CashFlowPoint> cashFlow(
            @RequestParam(required = false, defaultValue = "6") int months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return analyticsService.cashFlow(months, asOfOrToday(asOf));
    }

    @GetMapping("/kpi-summary")
    public KpiSummary kpiSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return analyticsService.kpiSummary(asOfOrToday(asOf));
    }

    @GetMapping("/budget-variance")
    public BudgetVarianceReport budgetVariance(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate reference = asOfOrToday(asOf);
        int resolvedYear = year != null ? year : reference.getYear();
        int resolvedMonth = month != null ? month : reference.getMonthValue();
        return analyticsService.budgetVariance(resolvedYear, resolvedMonth, reference);
    }

    private static LocalDate asOfOrToday(LocalDate asOf) {
        return asOf != null ? asOf : LocalDate.now();
    }
}
