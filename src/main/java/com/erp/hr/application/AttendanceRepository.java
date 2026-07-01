package com.erp.hr.application;

import com.erp.hr.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<Attendance> findByOrderByWorkDateDescIdDesc();

    List<Attendance> findByEmployeeIdOrderByWorkDateDescIdDesc(Long employeeId);

    List<Attendance> findByWorkDateBetweenOrderByWorkDateDescIdDesc(LocalDate from, LocalDate to);

    List<Attendance> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
            Long employeeId, LocalDate from, LocalDate to);
}
