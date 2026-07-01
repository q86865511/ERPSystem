package com.erp.hr.api;

import java.util.List;
import java.util.Optional;

/**
 * The hr module's published read port. Other modules (payroll, reporting) resolve employees, departments
 * and positions through this interface only — never importing hr's domain entities or repositories.
 */
public interface HrQuery {

    Optional<EmployeeView> findEmployee(Long id);

    Optional<EmployeeView> findEmployeeByCode(String code);

    List<EmployeeView> activeEmployees();

    Optional<DepartmentView> findDepartment(Long id);

    Optional<PositionView> findPosition(Long id);
}
