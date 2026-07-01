package com.erp.hr.application;

import com.erp.hr.domain.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    boolean existsByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    List<Payroll> findByOrderByPeriodYearDescPeriodMonthDesc();
}
