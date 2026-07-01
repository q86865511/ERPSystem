package com.erp.hr.application;

import com.erp.TestcontainersConfiguration;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.PayrollStatus;
import com.erp.hr.domain.Payroll;
import com.erp.hr.domain.PayrollLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payroll run/post service operations. Tests share the container, so each uses a distinct period and
 * asserts on its own employees' lines ("contains") rather than exact totals.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PayrollServiceIT {

    @Autowired
    private PayrollService payrollService;
    @Autowired
    private HrService hrService;

    private Long activeEmployee(String suffix, String salary) {
        Long dept = hrService.createDepartment("PR-D-" + suffix, "Dept " + suffix, null).getId();
        Long pos = hrService.createPosition("PR-P-" + suffix, "Pos " + suffix, null).getId();
        return hrService.createEmployee("PR-E-" + suffix, "Pay", "Roll", dept, pos, new BigDecimal(salary),
                EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1)).getId();
    }

    @Test
    void runBuildsLinesFromActiveEmployees() {
        Long emp = activeEmployee("A", "100000");
        Payroll p = payrollService.run(2026, 1);
        assertThat(p.getStatus()).isEqualTo(PayrollStatus.DRAFT);
        PayrollLine line = p.getLines().stream().filter(l -> l.getEmployeeId().equals(emp))
                .findFirst().orElseThrow();
        assertThat(line.getGross()).isEqualByComparingTo("100000.00");
        assertThat(line.getTax()).isEqualByComparingTo("6000.00");
        assertThat(line.getInsurance()).isEqualByComparingTo("5000.00");
        assertThat(line.getNet()).isEqualByComparingTo("89000.00");
    }

    @Test
    void duplicatePeriodIsRejected() {
        payrollService.run(2026, 2);
        assertThatThrownBy(() -> payrollService.run(2026, 2)).isInstanceOf(HrConflictException.class);
    }

    @Test
    void postMovesToPostedAndLinksTheEntry() {
        activeEmployee("C", "60000");
        Payroll draft = payrollService.run(2026, 3);
        Payroll posted = payrollService.post(draft.getId(), LocalDate.of(2026, 3, 31), "hr");
        assertThat(posted.getStatus()).isEqualTo(PayrollStatus.POSTED);
        assertThat(posted.getJournalEntryId()).isNotNull();
    }

    @Test
    void aPostedPayrollCannotBePostedAgain() {
        activeEmployee("D", "50000");
        Payroll draft = payrollService.run(2026, 4);
        payrollService.post(draft.getId(), LocalDate.of(2026, 4, 30), "hr");
        assertThatThrownBy(() -> payrollService.post(draft.getId(), LocalDate.of(2026, 4, 30), "hr"))
                .isInstanceOf(HrConflictException.class);
    }

    @Test
    void unknownPayrollIsRejected() {
        assertThatThrownBy(() -> payrollService.get(999_999L)).isInstanceOf(HrNotFoundException.class);
    }
}
