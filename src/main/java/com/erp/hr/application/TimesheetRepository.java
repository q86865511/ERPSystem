package com.erp.hr.application;

import com.erp.hr.api.TimesheetStatus;
import com.erp.hr.domain.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    boolean existsByEmployeeIdAndWeekEnding(Long employeeId, LocalDate weekEnding);

    List<Timesheet> findByOrderByWeekEndingDescIdDesc();

    List<Timesheet> findByStatusOrderByWeekEndingDescIdDesc(TimesheetStatus status);

    List<Timesheet> findByEmployeeIdOrderByWeekEndingDescIdDesc(Long employeeId);

    List<Timesheet> findByEmployeeIdAndStatusOrderByWeekEndingDescIdDesc(
            Long employeeId, TimesheetStatus status);
}
