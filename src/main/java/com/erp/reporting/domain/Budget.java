package com.erp.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * A monthly budget for one GL account in one fiscal year — the planned figure the budget-variance report
 * compares actual posted movement against. One row per (year, account).
 */
@Entity
@Table(name = "budget", uniqueConstraints =
        @UniqueConstraint(name = "uq_budget_year_account", columnNames = {"period_year", "account_code"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "monthly_budget", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyBudget;

    public Budget(int periodYear, String accountCode, BigDecimal monthlyBudget) {
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("accountCode is required");
        }
        if (monthlyBudget == null) {
            throw new IllegalArgumentException("monthlyBudget is required");
        }
        this.periodYear = periodYear;
        this.accountCode = accountCode;
        this.monthlyBudget = monthlyBudget;
    }
}
