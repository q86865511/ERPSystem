package com.erp.bootstrap;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.PayrollStatus;
import com.erp.hr.application.LeaveService;
import com.erp.hr.application.PayrollService;
import com.erp.ledger.api.GeneralLedgerQuery;
import com.erp.ledger.api.LedgerLineView;
import com.erp.manufacturing.application.ReorderReportService;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.reporting.application.ReconciliationReport;
import com.erp.reporting.application.ReconciliationReport.ClearingBalance;
import com.erp.reporting.application.ReconciliationReport.SubledgerCheck;
import com.erp.reporting.application.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code seed} profile's {@link DataSeeder} runs the whole buy → make → sell slice through
 * the real posting services and leaves the books reconciled. Beyond the canonical single cycle it also
 * seeds the richer, multi-month data set, so this test additionally guards the enrichment's invariants:
 * the AR/AP subledgers carry open (unpaid) balances that still equal their GL control accounts, and every
 * transitional clearing account has netted to zero — proving no receive/issue/delivery was left dangling.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("seed")
class SeedDataIT {

    @Autowired
    private MasterDataQuery masterDataQuery;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private ReorderReportService reorderReportService;
    @Autowired
    private LeaveService leaveService;
    @Autowired
    private PayrollService payrollService;
    @Autowired
    private GeneralLedgerQuery generalLedgerQuery;

    @Test
    void seedRunsTheWholeSliceAndReconciles() {
        Long raw = masterDataQuery.findItemBySku("RM-DEMO").orElseThrow().id();
        Long finished = masterDataQuery.findItemBySku("FG-DEMO").orElseThrow().id();

        // Canonical cycle is untouched. Raw: received 100, issued 50 into WIP -> 50 on hand. Finished:
        // produced 50, sold 30 -> 20.
        assertThat(itemCostStateRepository.findById(raw).orElseThrow().getOnHandQty())
                .isEqualByComparingTo("50");
        assertThat(itemCostStateRepository.findById(finished).orElseThrow().getOnHandQty())
                .isEqualByComparingTo("20");

        // The enrichment ran: new product families and materials exist.
        assertThat(masterDataQuery.findItemBySku("FG-PUMP")).isPresent();
        assertThat(masterDataQuery.findItemBySku("RM-STEEL")).isPresent();
        assertThat(masterDataQuery.findPartnerByCode("CUST-NORTH")).isPresent();

        // After the whole seed the books reconcile.
        ReconciliationReport report = reconciliationService.reconcile(LocalDate.of(2026, 12, 31));
        assertThat(report.trialBalanceBalanced()).isTrue();
        assertThat(report.healthy()).isTrue();

        // The enrichment leaves open AR and AP (invoiced/billed but unpaid), so their subledgers are
        // non-zero — and still exactly equal to their GL control accounts.
        SubledgerCheck ap = subledger(report, "2100");
        SubledgerCheck ar = subledger(report, "1200");
        assertThat(ap.subledger()).isGreaterThan(BigDecimal.ZERO);
        assertThat(ap.reconciled()).isTrue();
        assertThat(ar.subledger()).isGreaterThan(BigDecimal.ZERO);
        assertThat(ar.reconciled()).isTrue();

        // Every transitional clearing account (GR-IR, Deferred-COGS, WIP) has netted to zero — proving
        // every receive was billed, every issue completed and every delivery invoiced.
        assertThat(report.clearingAccounts()).isNotEmpty();
        assertThat(report.clearingAccounts()).allMatch(ClearingBalance::cleared);

        // The low-stock demo materials surface on the reorder report (they carry a small on-hand row
        // below their reorder point), so the reorder / low-stock widgets have data.
        assertThat(reorderReportService.reorderReport().items()).hasSizeGreaterThanOrEqualTo(3);

        // HR B2 time data seeded: a PENDING leave request exists for the approve/reject demo.
        assertThat(leaveService.list(null, LeaveStatus.PENDING)).isNotEmpty();

        // HR B3: a payroll run was posted to the ledger (and the books above still reconcile).
        assertThat(payrollService.list()).anyMatch(p -> p.getStatus() == PayrollStatus.POSTED);

        // Realism guard: revenue clears the salary bill. When the seed lands a full paid-sales round in a
        // payroll-free month (its monthAt(months, 4) = 2026-05, present once the run date is June+), compare
        // it against the roster's monthly salary bill (532k — that month itself has no payroll posting, so
        // the literal keeps the assertion meaningful). This catches a regression to the old undersized sale
        // prices, which made the demo read as a deeply unprofitable company.
        YearMonth paidSalesMonth = seededPaidSalesMonth();
        if (paidSalesMonth != null) {
            assertThat(creditNet("4100", paidSalesMonth)).as("revenue in %s", paidSalesMonth)
                    .isGreaterThan(new BigDecimal("532000"));
        } else {
            LocalDate yearEnd = LocalDate.of(2026, 12, 31);
            BigDecimal annualRevenue = accountTotal("4100", yearEnd, true);
            BigDecimal annualSalaries = accountTotal("6100", yearEnd, false);
            assertThat(annualRevenue).as("FY2026 revenue").isGreaterThan(annualSalaries);
        }
    }

    /**
     * The seeder's first full paid-sales month (its {@code monthAt(months, 4)} = 2026-05), which carries no
     * payroll — or {@code null} when the run date is too early in 2026 for that month to be seeded distinctly.
     */
    private static YearMonth seededPaidSalesMonth() {
        LocalDate today = LocalDate.now();
        // Mirror DataSeeder.seedRichDemoData: the 15th of each earlier 2026 month, then today. Index 4 is a
        // real May only once months Jan..(month-1) reach that far (i.e. the run month is June or later).
        return today.getYear() == 2026 && today.getMonthValue() - 1 >= 5 ? YearMonth.of(2026, 5) : null;
    }

    private BigDecimal creditNet(String accountCode, YearMonth ym) {
        return net(accountCode, ym, true);
    }

    /** Net posting for one account in one month: credit − debit when {@code credit}, else debit − credit. */
    private BigDecimal net(String accountCode, YearMonth ym, boolean credit) {
        List<LedgerLineView> lines = generalLedgerQuery.linesForAccount(accountCode, ym.atEndOfMonth());
        return net(lines, l -> YearMonth.from(l.postingDate()).equals(ym), credit);
    }

    /** Cumulative net posting for one account on or before {@code asOf} (all months). */
    private BigDecimal accountTotal(String accountCode, LocalDate asOf, boolean credit) {
        return net(generalLedgerQuery.linesForAccount(accountCode, asOf), l -> true, credit);
    }

    private static BigDecimal net(List<LedgerLineView> lines,
                                  java.util.function.Predicate<LedgerLineView> keep, boolean credit) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerLineView l : lines) {
            if (!keep.test(l)) {
                continue;
            }
            debits = debits.add(l.debit() != null ? l.debit() : BigDecimal.ZERO);
            credits = credits.add(l.credit() != null ? l.credit() : BigDecimal.ZERO);
        }
        return credit ? credits.subtract(debits) : debits.subtract(credits);
    }

    private static SubledgerCheck subledger(ReconciliationReport report, String accountCode) {
        return report.subledgerChecks().stream()
                .filter(c -> c.accountCode().equals(accountCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no subledger check for account " + accountCode));
    }
}
