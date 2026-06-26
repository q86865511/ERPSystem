package com.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * A VAT rate, keyed by a natural code (e.g. {@code STANDARD}). The MVP uses a single rate; storing it
 * as data (not a constant) lets later phases add rates and lets sales reuse it for output VAT.
 */
@Entity
@Table(name = "tax_rate")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class TaxRate {

    @Id
    private String code;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean active;

    public TaxRate(String code, BigDecimal rate) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (rate == null || rate.signum() < 0) {
            throw new IllegalArgumentException("rate must not be negative");
        }
        this.code = code;
        this.rate = rate;
        this.active = true;
    }
}
