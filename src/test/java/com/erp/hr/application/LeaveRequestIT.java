package com.erp.hr.application;

import com.erp.TestcontainersConfiguration;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.LeaveType;
import com.erp.hr.domain.LeaveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Leave requests and their approve/reject lifecycle. Tests share the container (unique codes). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LeaveRequestIT {

    @Autowired
    private LeaveService leaveService;
    @Autowired
    private HrService hrService;

    private Long employee(String suffix) {
        Long dept = hrService.createDepartment("LV-D-" + suffix, "Dept " + suffix, null).getId();
        Long pos = hrService.createPosition("LV-P-" + suffix, "Pos " + suffix, null).getId();
        return hrService.createEmployee("LV-E-" + suffix, "Lee", "Ave", dept, pos, null,
                EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1)).getId();
    }

    private LeaveRequest pending(String suffix) {
        return leaveService.submit(employee(suffix), LeaveType.ANNUAL, LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 8), new BigDecimal("3"), "vacation");
    }

    @Test
    void submitStartsPendingAndLists() {
        LeaveRequest lr = pending("SUB");
        assertThat(lr.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(leaveService.list(null, LeaveStatus.PENDING)).extracting(LeaveRequest::getId)
                .contains(lr.getId());
    }

    @Test
    void approveRecordsTheDecision() {
        LeaveRequest lr = leaveService.approve(pending("APP").getId(), "hr");
        assertThat(lr.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(lr.getDecidedBy()).isEqualTo("hr");
        assertThat(lr.getDecidedOn()).isNotNull();
    }

    @Test
    void rejectMovesToRejected() {
        LeaveRequest lr = leaveService.reject(pending("REJ").getId(), "hr");
        assertThat(lr.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    void anAlreadyDecidedRequestCannotBeDecidedAgain() {
        Long id = pending("TWICE").getId();
        leaveService.approve(id, "hr");
        assertThatThrownBy(() -> leaveService.reject(id, "hr")).isInstanceOf(HrConflictException.class);
    }

    @Test
    void unknownEmployeeIsRejected() {
        assertThatThrownBy(() -> leaveService.submit(999_999L, LeaveType.SICK, LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1), new BigDecimal("1"), null))
                .isInstanceOf(HrNotFoundException.class);
    }
}
