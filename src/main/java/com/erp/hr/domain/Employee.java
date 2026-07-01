package com.erp.hr.domain;

import com.erp.hr.api.EmploymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

/**
 * An employee master record: identity, department/position assignment, monthly salary and employment
 * status. Cross-aggregate references (department, position) are held by id — not JPA associations — keeping
 * the aggregate boundary clean.
 */
@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "monthly_salary", precision = 19, scale = 4)
    private BigDecimal monthlySalary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus status;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    public Employee(String code, String firstName, String lastName, Long departmentId, Long positionId,
                    BigDecimal monthlySalary, EmploymentStatus status, LocalDate hireDate) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("firstName must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName must not be blank");
        }
        if (departmentId == null) {
            throw new IllegalArgumentException("departmentId is required");
        }
        if (positionId == null) {
            throw new IllegalArgumentException("positionId is required");
        }
        if (hireDate == null) {
            throw new IllegalArgumentException("hireDate is required");
        }
        this.code = code;
        this.firstName = firstName;
        this.lastName = lastName;
        this.departmentId = departmentId;
        this.positionId = positionId;
        this.monthlySalary = monthlySalary;
        this.status = status != null ? status : EmploymentStatus.ACTIVE;
        this.hireDate = hireDate;
    }
}
