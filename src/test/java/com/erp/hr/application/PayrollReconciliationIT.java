package com.erp.hr.application;

import com.erp.TestcontainersConfiguration;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.PayrollStatus;
import com.erp.hr.domain.Payroll;
import com.erp.reporting.application.ReconciliationReport;
import com.erp.reporting.application.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posting payroll writes one balanced journal entry that touches only GL accounts (salary expense and the
 * payable liabilities) — never a subledger control account. So the trial balance stays balanced and the
 * reconciliation health is unchanged by the post.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PayrollReconciliationIT {

    @Autowired
    private PayrollService payrollService;
    @Autowired
    private HrService hrService;
    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void postingPayrollKeepsTheBooksBalanced() {
        Long dept = hrService.createDepartment("PRR-D", "Payroll Recon Dept", null).getId();
        Long pos = hrService.createPosition("PRR-P", "Analyst", null).getId();
        hrService.createEmployee("PRR-E", "Pay", "Roll", dept, pos, new BigDecimal("80000"),
                EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1));

        LocalDate asOf = LocalDate.of(2026, 12, 31);
        ReconciliationReport before = reconciliationService.reconcile(asOf);

        Payroll draft = payrollService.run(2026, 10);
        assertThat(draft.getGrossTotal().signum()).isPositive();

        Payroll posted = payrollService.post(draft.getId(), LocalDate.of(2026, 10, 31), "hr");
        assertThat(posted.getStatus()).isEqualTo(PayrollStatus.POSTED);
        assertThat(posted.getJournalEntryId()).isNotNull();
        // The entry balances: gross debit = net + tax + insurance credits.
        assertThat(posted.getNetTotal().add(posted.getTaxTotal()).add(posted.getInsuranceTotal()))
                .isEqualByComparingTo(posted.getGrossTotal());

        ReconciliationReport after = reconciliationService.reconcile(asOf);
        assertThat(after.trialBalanceBalanced()).isTrue();
        // Payroll touches no subledger, so it cannot change reconciliation health.
        assertThat(after.healthy()).isEqualTo(before.healthy());
    }
}
