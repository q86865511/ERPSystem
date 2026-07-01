package com.erp.hr.web;

import com.erp.hr.api.AttendanceStatus;
import com.erp.hr.domain.Attendance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API view of a daily attendance record. */
public record AttendanceResponse(Long id, Long employeeId, LocalDate workDate, AttendanceStatus status,
                                 BigDecimal workedHours, String note) {

    public static AttendanceResponse from(Attendance a) {
        return new AttendanceResponse(a.getId(), a.getEmployeeId(), a.getWorkDate(), a.getStatus(),
                a.getWorkedHours(), a.getNote());
    }
}
