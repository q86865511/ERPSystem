package com.erp.manufacturing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

/**
 * One equipment's production for a day: how long it was scheduled to run, how much of that was lost to
 * downtime (with the main reason), and how many units it produced vs. how many passed quality. These feed
 * the OEE calculation — Availability, Performance and Quality — and the downtime-by-reason breakdown.
 */
@Entity
@Table(name = "production_log", uniqueConstraints =
        @UniqueConstraint(name = "uq_production_log_equipment_day", columnNames = {"equipment_id", "log_date"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ProductionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "downtime_minutes", nullable = false)
    private int downtimeMinutes;

    @Column(name = "downtime_reason")
    private String downtimeReason;

    @Column(name = "produced_units", nullable = false)
    private int producedUnits;

    @Column(name = "good_units", nullable = false)
    private int goodUnits;

    public ProductionLog(Long equipmentId, LocalDate logDate, int plannedMinutes, int downtimeMinutes,
                         String downtimeReason, int producedUnits, int goodUnits) {
        if (equipmentId == null) {
            throw new IllegalArgumentException("equipmentId is required");
        }
        if (logDate == null) {
            throw new IllegalArgumentException("logDate is required");
        }
        if (plannedMinutes <= 0) {
            throw new IllegalArgumentException("plannedMinutes must be positive");
        }
        if (downtimeMinutes < 0 || downtimeMinutes > plannedMinutes) {
            throw new IllegalArgumentException("downtimeMinutes must be within [0, plannedMinutes]");
        }
        if (producedUnits < 0 || goodUnits < 0 || goodUnits > producedUnits) {
            throw new IllegalArgumentException("units must be non-negative and goodUnits <= producedUnits");
        }
        this.equipmentId = equipmentId;
        this.logDate = logDate;
        this.plannedMinutes = plannedMinutes;
        this.downtimeMinutes = downtimeMinutes;
        this.downtimeReason = downtimeReason;
        this.producedUnits = producedUnits;
        this.goodUnits = goodUnits;
    }
}
