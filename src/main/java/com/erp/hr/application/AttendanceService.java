package com.erp.hr.application;

import com.erp.hr.api.AttendanceStatus;
import com.erp.hr.domain.Attendance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Write and read operations for daily attendance records. */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public Attendance record(Long employeeId, LocalDate workDate, AttendanceStatus status,
                             BigDecimal workedHours, String note) {
        requireEmployee(employeeId);
        if (attendanceRepository.existsByEmployeeIdAndWorkDate(employeeId, workDate)) {
            throw new HrConflictException(
                    "attendance already recorded for employee " + employeeId + " on " + workDate);
        }
        return attendanceRepository.saveAndFlush(
                new Attendance(employeeId, workDate, status, workedHours, note));
    }

    /** Attendance records, optionally narrowed to an employee and/or a calendar month (newest first). */
    @Transactional(readOnly = true)
    public List<Attendance> list(Long employeeId, YearMonth month) {
        if (employeeId != null && month != null) {
            return attendanceRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                    employeeId, month.atDay(1), month.atEndOfMonth());
        }
        if (employeeId != null) {
            return attendanceRepository.findByEmployeeIdOrderByWorkDateDescIdDesc(employeeId);
        }
        if (month != null) {
            return attendanceRepository.findByWorkDateBetweenOrderByWorkDateDescIdDesc(
                    month.atDay(1), month.atEndOfMonth());
        }
        return attendanceRepository.findByOrderByWorkDateDescIdDesc();
    }

    private void requireEmployee(Long employeeId) {
        if (employeeId == null || !employeeRepository.existsById(employeeId)) {
            throw new HrNotFoundException("employee with id " + employeeId);
        }
    }
}
