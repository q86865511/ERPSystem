package com.erp.hr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/** An organizational department. Groups employees and carries the GL budget account used when payroll posts. */
@Entity
@Table(name = "department")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "budget_account_code")
    private String budgetAccountCode;

    @Column(nullable = false)
    private boolean active;

    public Department(String code, String name, String budgetAccountCode) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.code = code;
        this.name = name;
        this.budgetAccountCode = budgetAccountCode;
        this.active = true;
    }
}
