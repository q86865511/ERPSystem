package com.erp.hr.domain;

import com.erp.hr.api.AttendanceStatus;
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
 * A single employee's attendance for one calendar day: whether they were present, and how many hours they
 * worked. One record per employee per day (enforced by a unique constraint). The employee is referenced by
 * id, not a JPA association, keeping the aggregate boundary clean.
 */
@Entity
@Table(name = "attendance", uniqueConstraints =
        @UniqueConstraint(name = "uq_attendance_employee_day", columnNames = {"employee_id", "work_date"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Column(name = "worked_hours", precision = 6, scale = 2)
    private BigDecimal workedHours;

    @Column(length = 500)
    private String note;

    public Attendance(Long employeeId, LocalDate workDate, AttendanceStatus status, BigDecimal workedHours,
                      String note) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (workDate == null) {
            throw new IllegalArgumentException("workDate is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (workedHours != null && workedHours.signum() < 0) {
            throw new IllegalArgumentException("workedHours must not be negative");
        }
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.status = status;
        this.workedHours = workedHours;
        this.note = note;
    }
}
