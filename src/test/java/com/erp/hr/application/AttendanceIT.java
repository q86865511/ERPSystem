package com.erp.hr.application;

import com.erp.TestcontainersConfiguration;
import com.erp.hr.api.AttendanceStatus;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.domain.Attendance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Daily attendance records back the HR attendance tab. Tests share the container, so assertions use unique
 * codes and "contains" rather than exact counts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AttendanceIT {

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private HrService hrService;

    private Long employee(String suffix) {
        Long dept = hrService.createDepartment("ATT-D-" + suffix, "Dept " + suffix, null).getId();
        Long pos = hrService.createPosition("ATT-P-" + suffix, "Pos " + suffix, null).getId();
        return hrService.createEmployee("ATT-E-" + suffix, "Ann", "Att", dept, pos, null,
                EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1)).getId();
    }

    @Test
    void recordAndListByEmployeeAndMonth() {
        Long emp = employee("REC");
        Attendance a = attendanceService.record(emp, LocalDate.of(2026, 6, 10), AttendanceStatus.PRESENT,
                new BigDecimal("8"), null);

        assertThat(attendanceService.list(emp, null)).extracting(Attendance::getId).contains(a.getId());
        assertThat(attendanceService.list(emp, YearMonth.of(2026, 6)))
                .extracting(Attendance::getId).contains(a.getId());
        assertThat(attendanceService.list(emp, YearMonth.of(2026, 5))).isEmpty();
    }

    @Test
    void duplicateDayIsRejected() {
        Long emp = employee("DUP");
        attendanceService.record(emp, LocalDate.of(2026, 6, 11), AttendanceStatus.PRESENT, null, null);
        assertThatThrownBy(() -> attendanceService.record(emp, LocalDate.of(2026, 6, 11),
                AttendanceStatus.LATE, null, null)).isInstanceOf(HrConflictException.class);
    }

    @Test
    void unknownEmployeeIsRejected() {
        assertThatThrownBy(() -> attendanceService.record(999_999L, LocalDate.of(2026, 6, 12),
                AttendanceStatus.PRESENT, null, null)).isInstanceOf(HrNotFoundException.class);
    }
}
