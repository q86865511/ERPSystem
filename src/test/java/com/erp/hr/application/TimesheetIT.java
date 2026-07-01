package com.erp.hr.application;

import com.erp.TestcontainersConfiguration;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.TimesheetStatus;
import com.erp.hr.domain.Timesheet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Weekly timesheets and their draft → submitted → approved lifecycle. Tests share the container. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TimesheetIT {

    @Autowired
    private TimesheetService timesheetService;
    @Autowired
    private HrService hrService;

    private Long employee(String suffix) {
        Long dept = hrService.createDepartment("TS-D-" + suffix, "Dept " + suffix, null).getId();
        Long pos = hrService.createPosition("TS-P-" + suffix, "Pos " + suffix, null).getId();
        return hrService.createEmployee("TS-E-" + suffix, "Tim", "Sheet", dept, pos, null,
                EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1)).getId();
    }

    private Timesheet draft(String suffix) {
        return timesheetService.create(employee(suffix), LocalDate.of(2026, 7, 5), new BigDecimal("40"),
                new BigDecimal("5"), null);
    }

    @Test
    void createStartsDraftAndLists() {
        Timesheet ts = draft("NEW");
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(timesheetService.list(null, TimesheetStatus.DRAFT)).extracting(Timesheet::getId)
                .contains(ts.getId());
    }

    @Test
    void submitThenApproveWalksTheLifecycle() {
        Long id = draft("LIFE").getId();
        assertThat(timesheetService.submit(id).getStatus()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(timesheetService.approve(id).getStatus()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void cannotSubmitANonDraft() {
        Long id = draft("SUB2").getId();
        timesheetService.submit(id);
        assertThatThrownBy(() -> timesheetService.submit(id)).isInstanceOf(HrConflictException.class);
    }

    @Test
    void cannotApproveANonSubmitted() {
        Long id = draft("APP2").getId();
        assertThatThrownBy(() -> timesheetService.approve(id)).isInstanceOf(HrConflictException.class);
    }

    @Test
    void duplicateWeekIsRejected() {
        Long emp = employee("DUP");
        timesheetService.create(emp, LocalDate.of(2026, 7, 12), new BigDecimal("40"), BigDecimal.ZERO, null);
        assertThatThrownBy(() -> timesheetService.create(emp, LocalDate.of(2026, 7, 12),
                new BigDecimal("30"), BigDecimal.ZERO, null)).isInstanceOf(HrConflictException.class);
    }

    @Test
    void unknownEmployeeIsRejected() {
        assertThatThrownBy(() -> timesheetService.create(999_999L, LocalDate.of(2026, 7, 5),
                new BigDecimal("40"), BigDecimal.ZERO, null)).isInstanceOf(HrNotFoundException.class);
    }
}
