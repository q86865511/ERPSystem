package com.erp.hr.web;

import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.LeaveType;
import com.erp.hr.domain.LeaveRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API view of a leave request. */
public record LeaveRequestResponse(Long id, Long employeeId, LeaveType leaveType, LocalDate startDate,
                                   LocalDate endDate, BigDecimal days, String reason, LeaveStatus status,
                                   String decidedBy, LocalDate decidedOn) {

    public static LeaveRequestResponse from(LeaveRequest r) {
        return new LeaveRequestResponse(r.getId(), r.getEmployeeId(), r.getLeaveType(), r.getStartDate(),
                r.getEndDate(), r.getDays(), r.getReason(), r.getStatus(), r.getDecidedBy(),
                r.getDecidedOn());
    }
}
