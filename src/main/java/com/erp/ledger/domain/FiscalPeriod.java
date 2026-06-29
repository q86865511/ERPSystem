package com.erp.ledger.domain;

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

import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

/**
 * A fiscal period (typically a calendar month). The posting service resolves a posting date to its
 * period and refuses to post unless the period is {@link FiscalPeriodStatus#OPEN}.
 */
@Entity
@Table(name = "fiscal_period")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FiscalPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year_id", nullable = false)
    private Long fiscalYearId;

    @Column(name = "period_no", nullable = false)
    private int periodNo;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalPeriodStatus status;

    public boolean isOpen() {
        return status == FiscalPeriodStatus.OPEN;
    }

    /** Soft close: blocks new postings into this period. */
    public void close() {
        this.status = FiscalPeriodStatus.CLOSED;
    }

    /** Re-opens a soft-closed period. */
    public void reopen() {
        this.status = FiscalPeriodStatus.OPEN;
    }

    /** Hard close: locks this period as part of the year-end close. A locked period cannot be reopened. */
    public void lock() {
        this.status = FiscalPeriodStatus.LOCKED;
    }

    public boolean isLocked() {
        return status == FiscalPeriodStatus.LOCKED;
    }
}
