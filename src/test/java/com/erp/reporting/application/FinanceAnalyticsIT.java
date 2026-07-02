package com.erp.reporting.application;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.reporting.domain.Budget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finance analytics aggregate from posted ledger lines. The suite shares the container, so assertions hold
 * regardless of other data: structural identities (grossMargin = revenue − cogs, net = inflow − outflow)
 * plus this test's own contributions reflected in the right month.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinanceAnalyticsIT {

    private static final LocalDate AS_OF = LocalDate.of(2026, 5, 31);

    @Autowired
    private FinanceAnalyticsService analyticsService;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private LedgerPosting ledgerPosting;

    @Test
    void trendsCashAndBudgetVarianceAggregateFromTheLedger() {
        // Revenue + cash inflow, then COGS + cash outflow, both posted in May 2026.
        ledgerPosting.post(new JournalEntryRequest(null, LocalDate.of(2026, 5, 15), "analytics revenue",
                null, "TEST-ANALYTICS", "REV-1", "POST", List.of(
                new Line("1010", new BigDecimal("1000"), null, "cash in"),
                new Line("4100", null, new BigDecimal("1000"), "revenue"))), "test");
        ledgerPosting.post(new JournalEntryRequest(null, LocalDate.of(2026, 5, 20), "analytics cogs",
                null, "TEST-ANALYTICS", "COGS-1", "POST", List.of(
                new Line("5100", new BigDecimal("400"), null, "cogs"),
                new Line("1010", null, new BigDecimal("400"), "cash out"))), "test");

        List<RevenuePoint> trend = analyticsService.revenueTrend(6, AS_OF);
        assertThat(trend).hasSize(6);
        RevenuePoint may = trend.get(trend.size() - 1);
        assertThat(may.month()).isEqualTo("2026-05");
        assertThat(may.revenue()).isGreaterThanOrEqualTo(new BigDecimal("1000"));
        assertThat(may.grossMargin()).isEqualByComparingTo(may.revenue().subtract(may.cogs()));

        List<CashFlowPoint> cash = analyticsService.cashFlow(6, AS_OF);
        CashFlowPoint mayCash = cash.get(cash.size() - 1);
        assertThat(mayCash.month()).isEqualTo("2026-05");
        assertThat(mayCash.inflow()).isGreaterThanOrEqualTo(new BigDecimal("1000"));
        assertThat(mayCash.net()).isEqualByComparingTo(mayCash.inflow().subtract(mayCash.outflow()));

        if (!budgetRepository.existsByPeriodYearAndAccountCode(2026, "4100")) {
            budgetRepository.saveAndFlush(new Budget(2026, "4100", new BigDecimal("800")));
        }
        BudgetVarianceReport variance = analyticsService.budgetVariance(2026, 5, AS_OF);
        BudgetVarianceReport.Line revenueLine = variance.lines().stream()
                .filter(l -> l.accountCode().equals("4100")).findFirst().orElseThrow();
        assertThat(revenueLine.budget()).isEqualByComparingTo("800");
        assertThat(revenueLine.variance()).isEqualByComparingTo(
                revenueLine.actual().subtract(revenueLine.budget()));

        KpiSummary kpi = analyticsService.kpiSummary(AS_OF);
        assertThat(kpi.currentMonth()).isEqualTo("2026-05");
        assertThat(kpi.revenue().current()).isNotNull();
        assertThat(kpi.grossProfit().current()).isNotNull();
    }
}
