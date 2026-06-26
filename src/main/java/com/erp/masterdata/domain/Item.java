package com.erp.masterdata.domain;

import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.ValuationMethod;
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

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * A product master record: raw material, finished good, work-in-process or service. {@code stocked}
 * items carry on-hand quantity and a moving-average cost; the (reserved) {@code valuationMethod}
 * column is MOVING_AVERAGE for the whole MVP.
 */
@Entity
@Table(name = "item")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(nullable = false)
    private String uom;

    @Column(nullable = false)
    private boolean stocked;

    @Column(name = "standard_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal standardCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "valuation_method", nullable = false)
    private ValuationMethod valuationMethod;

    @Column(name = "reorder_point", precision = 19, scale = 6)
    private BigDecimal reorderPoint;

    @Column(name = "reorder_qty", precision = 19, scale = 6)
    private BigDecimal reorderQty;

    public Item(String sku, String name, ItemType itemType, String uom, boolean stocked,
                BigDecimal standardCost, BigDecimal reorderPoint, BigDecimal reorderQty) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (itemType == null) {
            throw new IllegalArgumentException("itemType is required");
        }
        if (uom == null || uom.isBlank()) {
            throw new IllegalArgumentException("uom must not be blank");
        }
        BigDecimal cost = standardCost != null ? standardCost : BigDecimal.ZERO;
        if (cost.signum() < 0) {
            throw new IllegalArgumentException("standardCost must not be negative");
        }
        this.sku = sku;
        this.name = name;
        this.itemType = itemType;
        this.uom = uom;
        this.stocked = stocked;
        this.standardCost = cost;
        this.valuationMethod = ValuationMethod.MOVING_AVERAGE;
        this.reorderPoint = reorderPoint;
        this.reorderQty = reorderQty;
    }
}
