package com.erp.hr.web;

import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.domain.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API view of an employee. */
public record EmployeeResponse(Long id, String code, String firstName, String lastName, Long departmentId,
                               Long positionId, BigDecimal monthlySalary, EmploymentStatus status,
                               LocalDate hireDate, LocalDate terminationDate) {

    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(e.getId(), e.getCode(), e.getFirstName(), e.getLastName(),
                e.getDepartmentId(), e.getPositionId(), e.getMonthlySalary(), e.getStatus(),
                e.getHireDate(), e.getTerminationDate());
    }
}
