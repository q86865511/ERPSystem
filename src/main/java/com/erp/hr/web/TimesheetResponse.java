package com.erp.hr.web;

import com.erp.hr.api.TimesheetStatus;
import com.erp.hr.domain.Timesheet;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API view of a weekly timesheet. */
public record TimesheetResponse(Long id, Long employeeId, LocalDate weekEnding, BigDecimal regularHours,
                                BigDecimal overtimeHours, TimesheetStatus status, String note) {

    public static TimesheetResponse from(Timesheet t) {
        return new TimesheetResponse(t.getId(), t.getEmployeeId(), t.getWeekEnding(), t.getRegularHours(),
                t.getOvertimeHours(), t.getStatus(), t.getNote());
    }
}
