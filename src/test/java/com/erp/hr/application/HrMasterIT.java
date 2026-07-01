package com.erp.hr.application;

import com.erp.TestcontainersConfiguration;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.domain.Department;
import com.erp.hr.domain.Employee;
import com.erp.hr.domain.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HR master-data create/list operations back the frontend HR pages. Assertions use "contains" with unique
 * codes (tests share the container) rather than exact counts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HrMasterIT {

    @Autowired
    private HrService service;

    @Test
    void createAndListDepartments() {
        Department d = service.createDepartment("IT-DEPT", "IT", "6100");
        assertThat(service.listDepartments()).extracting(Department::getId).contains(d.getId());
    }

    @Test
    void createAndListPositions() {
        Position p = service.createPosition("IT-POS", "Engineer", new BigDecimal("70000.0000"));
        assertThat(service.listPositions()).extracting(Position::getId).contains(p.getId());
    }

    @Test
    void createEmployeeAndFilterByDepartment() {
        Department d = service.createDepartment("EMP-D1", "Dept One", null);
        Position p = service.createPosition("EMP-P1", "Analyst", null);
        Employee e = service.createEmployee("EMP-X1", "Ada", "Lovelace", d.getId(), p.getId(),
                new BigDecimal("60000.0000"), EmploymentStatus.ACTIVE, LocalDate.of(2023, 1, 2));

        assertThat(service.listEmployees()).extracting(Employee::getId).contains(e.getId());
        List<Employee> inDept = service.listEmployeesByDepartment(d.getId());
        assertThat(inDept).extracting(Employee::getId).contains(e.getId());
        assertThat(inDept).allSatisfy(x -> assertThat(x.getDepartmentId()).isEqualTo(d.getId()));
    }

    @Test
    void duplicateDepartmentCodeIsRejected() {
        service.createDepartment("DUP-D", "First", null);
        assertThatThrownBy(() -> service.createDepartment("DUP-D", "Second", null))
                .isInstanceOf(DuplicateCodeException.class);
    }

    @Test
    void employeeWithUnknownDepartmentIsRejected() {
        Position p = service.createPosition("BAD-P", "Nobody", null);
        assertThatThrownBy(() -> service.createEmployee("BAD-E", "No", "Dept", 999_999L, p.getId(),
                null, EmploymentStatus.ACTIVE, LocalDate.now()))
                .isInstanceOf(HrNotFoundException.class);
    }
}
