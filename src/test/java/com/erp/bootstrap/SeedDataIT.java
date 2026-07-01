package com.erp.bootstrap;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.hr.api.LeaveStatus;
import com.erp.hr.application.LeaveService;
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
    }

    private static SubledgerCheck subledger(ReconciliationReport report, String accountCode) {
        return report.subledgerChecks().stream()
                .filter(c -> c.accountCode().equals(accountCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no subledger check for account " + accountCode));
    }
}
