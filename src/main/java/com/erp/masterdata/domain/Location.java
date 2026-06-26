package com.erp.masterdata.domain;

import com.erp.masterdata.api.LocationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * A typed bin within a warehouse. Real stock lives in STOCK locations; the other types are virtual
 * counterparties (RECEIVING, SHIPPING, PRODUCTION_WIP, SCRAP, VENDOR, CUSTOMER, INVENTORY_LOSS) so
 * every stock movement is a balanced transfer between two locations. The warehouse is referenced by
 * id (cross-aggregate reference), not a JPA association.
 */
@Entity
@Table(name = "location", uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "code"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @Column(nullable = false)
    private boolean active;

    public Location(Long warehouseId, String code, LocationType locationType) {
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (locationType == null) {
            throw new IllegalArgumentException("locationType is required");
        }
        this.warehouseId = warehouseId;
        this.code = code;
        this.locationType = locationType;
        this.active = true;
    }
}
