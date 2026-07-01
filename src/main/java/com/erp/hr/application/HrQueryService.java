package com.erp.hr.application;

import com.erp.hr.api.DepartmentView;
import com.erp.hr.api.EmployeeView;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.HrQuery;
import com.erp.hr.api.PositionView;
import com.erp.hr.domain.Department;
import com.erp.hr.domain.Employee;
import com.erp.hr.domain.Position;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Implements the published {@link HrQuery} port other modules use to resolve HR master data. */
@Service
public class HrQueryService implements HrQuery {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;

    public HrQueryService(DepartmentRepository departmentRepository, PositionRepository positionRepository,
                          EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeView> findEmployee(Long id) {
        return employeeRepository.findById(id).map(HrQueryService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeView> findEmployeeByCode(String code) {
        return employeeRepository.findByCode(code).map(HrQueryService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeView> activeEmployees() {
        return employeeRepository.findByStatusOrderByCode(EmploymentStatus.ACTIVE).stream()
                .map(HrQueryService::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DepartmentView> findDepartment(Long id) {
        return departmentRepository.findById(id).map(HrQueryService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PositionView> findPosition(Long id) {
        return positionRepository.findById(id).map(HrQueryService::toView);
    }

    static DepartmentView toView(Department d) {
        return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.getBudgetAccountCode(), d.isActive());
    }

    static PositionView toView(Position p) {
        return new PositionView(p.getId(), p.getCode(), p.getTitle(), p.getStandardSalary(), p.isActive());
    }

    static EmployeeView toView(Employee e) {
        return new EmployeeView(e.getId(), e.getCode(), e.getFirstName(), e.getLastName(),
                e.getDepartmentId(), e.getPositionId(), e.getMonthlySalary(), e.getStatus(),
                e.getHireDate(), e.getTerminationDate());
    }
}
