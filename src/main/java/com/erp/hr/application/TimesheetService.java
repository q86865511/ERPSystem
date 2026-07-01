package com.erp.hr.application;

import com.erp.hr.api.TimesheetStatus;
import com.erp.hr.domain.Timesheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Logs weekly timesheets and drives their draft → submitted → approved lifecycle. */
@Service
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final EmployeeRepository employeeRepository;

    public TimesheetService(TimesheetRepository timesheetRepository,
                            EmployeeRepository employeeRepository) {
        this.timesheetRepository = timesheetRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public Timesheet create(Long employeeId, LocalDate weekEnding, BigDecimal regularHours,
                            BigDecimal overtimeHours, String note) {
        requireEmployee(employeeId);
        if (timesheetRepository.existsByEmployeeIdAndWeekEnding(employeeId, weekEnding)) {
            throw new HrConflictException("timesheet already exists for employee " + employeeId
                    + " week ending " + weekEnding);
        }
        return timesheetRepository.saveAndFlush(
                new Timesheet(employeeId, weekEnding, regularHours, overtimeHours, note));
    }

    @Transactional
    public Timesheet submit(Long id) {
        Timesheet timesheet = get(id);
        if (timesheet.getStatus() != TimesheetStatus.DRAFT) {
            throw new HrConflictException(
                    "timesheet " + id + " is not DRAFT, was " + timesheet.getStatus());
        }
        timesheet.submit();
        return timesheetRepository.saveAndFlush(timesheet);
    }

    @Transactional
    public Timesheet approve(Long id) {
        Timesheet timesheet = get(id);
        if (timesheet.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new HrConflictException(
                    "timesheet " + id + " is not SUBMITTED, was " + timesheet.getStatus());
        }
        timesheet.approve();
        return timesheetRepository.saveAndFlush(timesheet);
    }

    @Transactional(readOnly = true)
    public List<Timesheet> list(Long employeeId, TimesheetStatus status) {
        if (employeeId != null && status != null) {
            return timesheetRepository.findByEmployeeIdAndStatusOrderByWeekEndingDescIdDesc(
                    employeeId, status);
        }
        if (employeeId != null) {
            return timesheetRepository.findByEmployeeIdOrderByWeekEndingDescIdDesc(employeeId);
        }
        if (status != null) {
            return timesheetRepository.findByStatusOrderByWeekEndingDescIdDesc(status);
        }
        return timesheetRepository.findByOrderByWeekEndingDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Timesheet get(Long id) {
        return timesheetRepository.findById(id)
                .orElseThrow(() -> new HrNotFoundException("timesheet with id " + id));
    }

    private void requireEmployee(Long employeeId) {
        if (employeeId == null || !employeeRepository.existsById(employeeId)) {
            throw new HrNotFoundException("employee with id " + employeeId);
        }
    }
}
