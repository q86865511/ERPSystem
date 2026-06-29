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

/** A fiscal year, partitioned into fiscal periods. */
@Entity
@Table(name = "fiscal_year")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FiscalYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalYearStatus status;

    /** Marks the year closed once its year-end carry-forward has posted and its periods are locked. */
    public void close() {
        this.status = FiscalYearStatus.CLOSED;
    }

    public boolean isClosed() {
        return status == FiscalYearStatus.CLOSED;
    }
}
