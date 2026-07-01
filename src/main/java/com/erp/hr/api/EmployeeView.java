package com.erp.hr.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Published read view of an employee — what other modules (e.g. payroll posting) need without the entity. */
public record EmployeeView(Long id, String code, String firstName, String lastName, Long departmentId,
                           Long positionId, BigDecimal monthlySalary, EmploymentStatus status,
                           LocalDate hireDate, LocalDate terminationDate) {
}
