package com.erp.hr.domain;

import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.LeaveType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Unit tests for the {@link LeaveRequest} filing → approve/reject state machine. */
class LeaveRequestTest {

    private static final LocalDate D1 = LocalDate.of(2026, 7, 6);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 8);
    private static final LocalDate DECIDED = LocalDate.of(2026, 7, 2);

    private LeaveRequest pending() {
        return new LeaveRequest(1L, LeaveType.ANNUAL, D1, D3, new BigDecimal("3"), "vacation");
    }

    @Test
    void aFiledRequestStartsPending() {
        assertThat(pending().getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void approveMovesToApprovedAndRecordsTheDecision() {
        LeaveRequest lr = pending();
        lr.approve("hr", DECIDED);
        assertThat(lr.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(lr.getDecidedBy()).isEqualTo("hr");
        assertThat(lr.getDecidedOn()).isEqualTo(DECIDED);
    }

    @Test
    void rejectMovesToRejected() {
        LeaveRequest lr = pending();
        lr.reject("hr", DECIDED);
        assertThat(lr.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    void anAlreadyDecidedRequestCannotBeDecidedAgain() {
        LeaveRequest lr = pending();
        lr.approve("hr", DECIDED);
        assertThatIllegalStateException().isThrownBy(() -> lr.reject("hr", DECIDED));
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LeaveRequest(1L, LeaveType.SICK, D3, D1, new BigDecimal("1"), null));
    }

    @Test
    void nonPositiveDaysIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LeaveRequest(1L, LeaveType.SICK, D1, D1, BigDecimal.ZERO, null));
    }
}
