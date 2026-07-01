package com.erp.hr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/** A job position (title) with a standard monthly salary band; the employee's own salary overrides it. */
@Entity
@Table(name = "position")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "standard_salary", precision = 19, scale = 4)
    private BigDecimal standardSalary;

    @Column(nullable = false)
    private boolean active;

    public Position(String code, String title, BigDecimal standardSalary) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        this.code = code;
        this.title = title;
        this.standardSalary = standardSalary;
        this.active = true;
    }
}
