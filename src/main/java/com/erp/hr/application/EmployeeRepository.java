package com.erp.hr.application;

import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByCode(String code);

    boolean existsByCode(String code);

    List<Employee> findByDepartmentIdOrderByCode(Long departmentId);

    List<Employee> findByStatusOrderByCode(EmploymentStatus status);
}
