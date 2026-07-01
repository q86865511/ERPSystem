package com.erp.hr.domain;

import com.erp.hr.api.PayrollStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static lombok.AccessLevel.PROTECTED;

/**
 * A payroll run for one calendar month. It is calculated as {@code DRAFT} from the active employees (one
 * {@link PayrollLine} each), then POSTED — which hands a single balanced journal entry to the ledger
 * (Dr salaries expense / Cr net-pay + tax + insurance payable) and records the resulting entry id. One run
 * per period (unique year+month).
 */
@Entity
@Table(name = "payroll", uniqueConstraints =
        @UniqueConstraint(name = "uq_payroll_period", columnNames = {"period_year", "period_month"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus status;

    @Column(name = "gross_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossTotal;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "insurance_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal insuranceTotal;

    @Column(name = "net_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal netTotal;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PayrollLine> lines = new ArrayList<>();

    public Payroll(int periodYear, int periodMonth) {
        if (periodMonth < 1 || periodMonth > 12) {
            throw new IllegalArgumentException("periodMonth must be 1..12, was " + periodMonth);
        }
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.status = PayrollStatus.DRAFT;
        this.grossTotal = BigDecimal.ZERO;
        this.taxTotal = BigDecimal.ZERO;
        this.insuranceTotal = BigDecimal.ZERO;
        this.netTotal = BigDecimal.ZERO;
    }

    /** Adds an employee's line and rolls it into the totals. Only allowed while DRAFT. */
    public void addLine(Long employeeId, BigDecimal gross, BigDecimal tax, BigDecimal insurance,
                        BigDecimal net) {
        if (status != PayrollStatus.DRAFT) {
            throw new IllegalStateException("can only add lines to a DRAFT payroll, was " + status);
        }
        lines.add(new PayrollLine(this, employeeId, gross, tax, insurance, net));
        this.grossTotal = grossTotal.add(gross);
        this.taxTotal = taxTotal.add(tax);
        this.insuranceTotal = insuranceTotal.add(insurance);
        this.netTotal = netTotal.add(net);
    }

    /** Records that this run has been posted to the ledger. Only a DRAFT run can be posted. */
    public void markPosted(Long journalEntryId, LocalDate postingDate) {
        if (status != PayrollStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT payroll can be posted, was " + status);
        }
        this.status = PayrollStatus.POSTED;
        this.journalEntryId = journalEntryId;
        this.postingDate = postingDate;
    }

    public List<PayrollLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
