package com.erp.hr.domain;

import com.erp.hr.api.TimesheetStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Unit tests for the {@link Timesheet} DRAFT → SUBMITTED → APPROVED state machine. */
class TimesheetTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 7, 5);

    private Timesheet draft() {
        return new Timesheet(1L, WEEK, new BigDecimal("40"), new BigDecimal("5"), null);
    }

    @Test
    void aNewTimesheetStartsDraft() {
        assertThat(draft().getStatus()).isEqualTo(TimesheetStatus.DRAFT);
    }

    @Test
    void submitThenApproveWalksTheLifecycle() {
        Timesheet ts = draft();
        ts.submit();
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.SUBMITTED);
        ts.approve();
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void cannotSubmitANonDraft() {
        Timesheet ts = draft();
        ts.submit();
        assertThatIllegalStateException().isThrownBy(ts::submit);
    }

    @Test
    void cannotApproveANonSubmitted() {
        assertThatIllegalStateException().isThrownBy(draft()::approve);
    }

    @Test
    void negativeHoursAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Timesheet(1L, WEEK, new BigDecimal("-1"), BigDecimal.ZERO, null));
    }

    @Test
    void nullHoursDefaultToZero() {
        Timesheet ts = new Timesheet(1L, WEEK, null, null, null);
        assertThat(ts.getRegularHours()).isEqualByComparingTo("0");
        assertThat(ts.getOvertimeHours()).isEqualByComparingTo("0");
    }
}
