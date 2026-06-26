package com.erp.reporting.application;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the financial statements. The Testcontainers database accumulates postings across
 * the suite, so this asserts the accounting identities (which hold regardless of other data) and the
 * deltas from a controlled capital + sale + expense scenario.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinancialStatementsIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final LocalDate AS_OF = LocalDate.of(2026, 12, 31);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private ReportingService reportingService;
    @Autowired
    private LedgerPosting ledgerPosting;

    @Test
    void statementsHoldTheAccountingIdentitiesAndReflectPostings() {
        BalanceSheet before = reportingService.balanceSheet(AS_OF);
        IncomeStatement beforeIs = reportingService.incomeStatement(AS_OF);

        // Use only non-subledger accounts (cash/capital/revenue/expense) so the shared container's
        // AR/AP/inventory reconciliation invariants in other tests are unaffected.
        int n = SEQ.incrementAndGet();
        post("CAP-" + n, new Line("1010", new BigDecimal("1000"), null, "cash"),
                new Line("3100", null, new BigDecimal("1000"), "share capital"));
        post("SALE-" + n, new Line("1010", new BigDecimal("600"), null, "cash"),
                new Line("4100", null, new BigDecimal("600"), "revenue"));
        post("EXP-" + n, new Line("5100", new BigDecimal("250"), null, "cost of sales"),
                new Line("1010", null, new BigDecimal("250"), "cash"));

        BalanceSheet after = reportingService.balanceSheet(AS_OF);
        IncomeStatement afterIs = reportingService.incomeStatement(AS_OF);
        TrialBalance afterTb = reportingService.trialBalance(AS_OF);

        // Identities: trial balance and balance sheet always balance; net income is revenue − expenses.
        assertThat(afterTb.balanced()).isTrue();
        assertThat(after.balanced()).isTrue();
        assertThat(afterIs.netIncome())
                .isEqualByComparingTo(afterIs.totalRevenue().subtract(afterIs.totalExpenses()));
        assertThat(after.netIncome()).isEqualByComparingTo(afterIs.netIncome());

        // Deltas from this scenario: revenue 600 − expense 250 = 350 net income.
        assertThat(afterIs.netIncome().subtract(beforeIs.netIncome())).isEqualByComparingTo("350");
        // Assets: cash +1000 −250, AR +600 = +1350. Equity: capital +1000 + earnings +350 = +1350.
        assertThat(after.totalAssets().subtract(before.totalAssets())).isEqualByComparingTo("1350");
        assertThat(after.totalEquity().subtract(before.totalEquity())).isEqualByComparingTo("1350");
    }

    private void post(String docId, Line... lines) {
        JournalEntryRequest request = new JournalEntryRequest(null, JUNE, "reporting test " + docId,
                null, "REPORTING_TEST", docId, "POSTED", List.of(lines));
        ledgerPosting.post(request, "tester");
    }
}
