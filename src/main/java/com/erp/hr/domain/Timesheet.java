package com.erp.hr.domain;

import com.erp.hr.api.TimesheetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

/**
 * A week's worked hours for one employee: regular plus overtime, identified by the week-ending date (one
 * per employee per week). It is logged as {@code DRAFT}, SUBMITTED for review, then APPROVED — a small
 * state machine. Distinct from attendance (daily presence): a timesheet is the periodic hours tally that
 * payroll (B3) can later use to pay overtime.
 */
@Entity
@Table(name = "timesheet", uniqueConstraints =
        @UniqueConstraint(name = "uq_timesheet_employee_week", columnNames = {"employee_id", "week_ending"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Timesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "week_ending", nullable = false)
    private LocalDate weekEnding;

    @Column(name = "regular_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal regularHours;

    @Column(name = "overtime_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal overtimeHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status;

    @Column(length = 500)
    private String note;

    public Timesheet(Long employeeId, LocalDate weekEnding, BigDecimal regularHours,
                     BigDecimal overtimeHours, String note) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (weekEnding == null) {
            throw new IllegalArgumentException("weekEnding is required");
        }
        BigDecimal regular = regularHours != null ? regularHours : BigDecimal.ZERO;
        BigDecimal overtime = overtimeHours != null ? overtimeHours : BigDecimal.ZERO;
        if (regular.signum() < 0 || overtime.signum() < 0) {
            throw new IllegalArgumentException("hours must not be negative");
        }
        this.employeeId = employeeId;
        this.weekEnding = weekEnding;
        this.regularHours = regular;
        this.overtimeHours = overtime;
        this.note = note;
        this.status = TimesheetStatus.DRAFT;
    }

    /** Submit a draft timesheet for review. Only a DRAFT can be submitted. */
    public void submit() {
        if (status != TimesheetStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT timesheet can be submitted, was " + status);
        }
        this.status = TimesheetStatus.SUBMITTED;
    }

    /** Approve a submitted timesheet. Only a SUBMITTED timesheet can be approved. */
    public void approve() {
        if (status != TimesheetStatus.SUBMITTED) {
            throw new IllegalStateException("only a SUBMITTED timesheet can be approved, was " + status);
        }
        this.status = TimesheetStatus.APPROVED;
    }
}
