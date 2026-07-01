package com.erp.hr.domain;

import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.LeaveType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

/**
 * A leave request an employee takes off work. It is filed {@code PENDING} and then approved or rejected by
 * HR — a small state machine that mirrors the order/work-order lifecycles elsewhere. It posts nothing to the
 * ledger; unpaid leave affects pay only when payroll (B3) reads it.
 */
@Entity
@Table(name = "leave_request")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal days;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    public LeaveRequest(Long employeeId, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                        BigDecimal days, String reason) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (leaveType == null) {
            throw new IllegalArgumentException("leaveType is required");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        if (days == null || days.signum() <= 0) {
            throw new IllegalArgumentException("days must be positive");
        }
        this.employeeId = employeeId;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
        this.status = LeaveStatus.PENDING;
    }

    /** Approve a pending request. Only a PENDING request can be decided. */
    public void approve(String actor, LocalDate decidedOn) {
        decide(LeaveStatus.APPROVED, actor, decidedOn);
    }

    /** Reject a pending request. Only a PENDING request can be decided. */
    public void reject(String actor, LocalDate decidedOn) {
        decide(LeaveStatus.REJECTED, actor, decidedOn);
    }

    private void decide(LeaveStatus outcome, String actor, LocalDate decidedOn) {
        if (status != LeaveStatus.PENDING) {
            throw new IllegalStateException("only a PENDING leave request can be decided, was " + status);
        }
        this.status = outcome;
        this.decidedBy = actor;
        this.decidedOn = decidedOn;
    }
}
