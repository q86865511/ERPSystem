package com.erp.hr.application;

import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.domain.Department;
import com.erp.hr.domain.Employee;
import com.erp.hr.domain.Position;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Write-side HR operations plus the reads backing the HR pages: departments, positions and employees. */
@Service
public class HrService {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;

    public HrService(DepartmentRepository departmentRepository, PositionRepository positionRepository,
                     EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public Department createDepartment(String code, String name, String budgetAccountCode) {
        if (departmentRepository.existsByCode(code)) {
            throw new DuplicateCodeException("department", code);
        }
        return departmentRepository.saveAndFlush(new Department(code, name, budgetAccountCode));
    }

    @Transactional
    public Position createPosition(String code, String title, BigDecimal standardSalary) {
        if (positionRepository.existsByCode(code)) {
            throw new DuplicateCodeException("position", code);
        }
        return positionRepository.saveAndFlush(new Position(code, title, standardSalary));
    }

    @Transactional
    public Employee createEmployee(String code, String firstName, String lastName, Long departmentId,
                                   Long positionId, BigDecimal monthlySalary, EmploymentStatus status,
                                   LocalDate hireDate) {
        if (employeeRepository.existsByCode(code)) {
            throw new DuplicateCodeException("employee", code);
        }
        if (departmentId == null || !departmentRepository.existsById(departmentId)) {
            throw new HrNotFoundException("department with id " + departmentId);
        }
        if (positionId == null || !positionRepository.existsById(positionId)) {
            throw new HrNotFoundException("position with id " + positionId);
        }
        Employee employee = new Employee(code, firstName, lastName, departmentId, positionId,
                monthlySalary, status, hireDate);
        return employeeRepository.saveAndFlush(employee);
    }

    @Transactional(readOnly = true)
    public List<Department> listDepartments() {
        return departmentRepository.findAll(Sort.by("code"));
    }

    @Transactional(readOnly = true)
    public List<Position> listPositions() {
        return positionRepository.findAll(Sort.by("code"));
    }

    @Transactional(readOnly = true)
    public List<Employee> listEmployees() {
        return employeeRepository.findAll(Sort.by("code"));
    }

    @Transactional(readOnly = true)
    public List<Employee> listEmployeesByDepartment(Long departmentId) {
        return employeeRepository.findByDepartmentIdOrderByCode(departmentId);
    }

    @Transactional(readOnly = true)
    public Department getDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new HrNotFoundException("department with id " + id));
    }

    @Transactional(readOnly = true)
    public Position getPosition(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new HrNotFoundException("position with id " + id));
    }

    @Transactional(readOnly = true)
    public Employee getEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new HrNotFoundException("employee with id " + id));
    }
}
