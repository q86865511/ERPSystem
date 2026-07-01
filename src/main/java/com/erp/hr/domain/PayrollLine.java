package com.erp.hr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * One employee's line on a payroll run — their payslip: gross pay, the tax and insurance withheld, and the
 * resulting net pay. {@code gross = tax + insurance + net} always (net absorbs any rounding). The employee
 * is referenced by id, keeping the aggregate boundary clean.
 */
@Entity
@Table(name = "payroll_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class PayrollLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_id", nullable = false)
    private Payroll payroll;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal gross;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal insurance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal net;

    PayrollLine(Payroll payroll, Long employeeId, BigDecimal gross, BigDecimal tax, BigDecimal insurance,
                BigDecimal net) {
        this.payroll = payroll;
        this.employeeId = employeeId;
        this.gross = gross;
        this.tax = tax;
        this.insurance = insurance;
        this.net = net;
    }
}
