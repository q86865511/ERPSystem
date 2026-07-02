package com.erp.hr.web;

import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.application.HrService;
import com.erp.hr.domain.Employee;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for HR master data: departments, positions and employees. */
@RestController
@RequestMapping("/api/hr")
public class HrController {

    private final HrService hrService;

    public HrController(HrService hrService) {
        this.hrService = hrService;
    }

    // budgetAccountCode / standardSalary / monthlySalary / status are optional at the domain boundary.
    public record CreateDepartmentRequest(@NotBlank String code, @NotBlank String name,
                                          String budgetAccountCode) {
    }

    public record CreatePositionRequest(@NotBlank String code, @NotBlank String title,
                                        BigDecimal standardSalary) {
    }

    public record CreateEmployeeRequest(@NotBlank String code, @NotBlank String firstName,
                                        @NotBlank String lastName, @NotNull Long departmentId,
                                        @NotNull Long positionId, BigDecimal monthlySalary,
                                        EmploymentStatus status, @NotNull LocalDate hireDate) {
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse body = DepartmentResponse.from(
                hrService.createDepartment(request.code(), request.name(), request.budgetAccountCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/departments/{id}")
    public DepartmentResponse getDepartment(@PathVariable Long id) {
        return DepartmentResponse.from(hrService.getDepartment(id));
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> listDepartments() {
        return hrService.listDepartments().stream().map(DepartmentResponse::from).toList();
    }

    @PostMapping("/positions")
    public ResponseEntity<PositionResponse> createPosition(@Valid @RequestBody CreatePositionRequest request) {
        PositionResponse body = PositionResponse.from(
                hrService.createPosition(request.code(), request.title(), request.standardSalary()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/positions/{id}")
    public PositionResponse getPosition(@PathVariable Long id) {
        return PositionResponse.from(hrService.getPosition(id));
    }

    @GetMapping("/positions")
    public List<PositionResponse> listPositions() {
        return hrService.listPositions().stream().map(PositionResponse::from).toList();
    }

    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse body = EmployeeResponse.from(hrService.createEmployee(
                request.code(), request.firstName(), request.lastName(), request.departmentId(),
                request.positionId(), request.monthlySalary(), request.status(), request.hireDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/employees/{id}")
    public EmployeeResponse getEmployee(@PathVariable Long id) {
        return EmployeeResponse.from(hrService.getEmployee(id));
    }

    @GetMapping("/employees")
    public List<EmployeeResponse> listEmployees(@RequestParam(required = false) Long departmentId) {
        List<Employee> employees = departmentId != null
                ? hrService.listEmployeesByDepartment(departmentId)
                : hrService.listEmployees();
        return employees.stream().map(EmployeeResponse::from).toList();
    }
}
