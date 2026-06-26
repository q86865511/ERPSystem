package com.erp.manufacturing.domain;

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
 * A bill-of-materials component line: {@code qtyPer} units of a component to produce the BOM's output
 * quantity of the parent. {@code scrapPct} is a reserved column for a future scrap allowance.
 */
@Entity
@Table(name = "bom_component")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BomComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id", nullable = false)
    private BillOfMaterials bom;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "component_item_id", nullable = false)
    private Long componentItemId;

    @Column(name = "qty_per", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyPer;

    @Column(name = "scrap_pct", nullable = false, precision = 9, scale = 6)
    private BigDecimal scrapPct;

    BomComponent(BillOfMaterials bom, int lineNo, Long componentItemId, BigDecimal qtyPer,
                 BigDecimal scrapPct) {
        if (componentItemId == null) {
            throw new IllegalArgumentException("componentItemId is required");
        }
        if (qtyPer == null || qtyPer.signum() <= 0) {
            throw new IllegalArgumentException("qtyPer must be positive");
        }
        this.bom = bom;
        this.lineNo = lineNo;
        this.componentItemId = componentItemId;
        this.qtyPer = qtyPer;
        this.scrapPct = scrapPct != null ? scrapPct : BigDecimal.ZERO;
    }
}
