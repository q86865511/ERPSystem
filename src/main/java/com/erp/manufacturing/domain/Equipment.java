package com.erp.manufacturing.domain;

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

/**
 * A production machine tracked for OEE. Its {@code idealUnitsPerHour} is the theoretical run rate used to
 * compute the performance component (actual output vs. what the machine could ideally produce while running).
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "ideal_units_per_hour", nullable = false, precision = 19, scale = 2)
    private BigDecimal idealUnitsPerHour;

    @Column(nullable = false)
    private boolean active;

    public Equipment(String code, String name, BigDecimal idealUnitsPerHour) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (idealUnitsPerHour == null || idealUnitsPerHour.signum() <= 0) {
            throw new IllegalArgumentException("idealUnitsPerHour must be positive");
        }
        this.code = code;
        this.name = name;
        this.idealUnitsPerHour = idealUnitsPerHour;
        this.active = true;
    }
}
