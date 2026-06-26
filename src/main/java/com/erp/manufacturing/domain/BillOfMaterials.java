package com.erp.manufacturing.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static lombok.AccessLevel.PROTECTED;

/**
 * A single-level bill of materials: the components consumed to produce {@code outputQty} units of a
 * parent (finished) item. Master data — it posts nothing; a work order snapshots it at release.
 */
@Entity
@Table(name = "bill_of_materials")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BillOfMaterials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_item_id", nullable = false)
    private Long parentItemId;

    @Column(nullable = false)
    private int version;

    @Column(name = "output_qty", nullable = false, precision = 19, scale = 6)
    private BigDecimal outputQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BomStatus status;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("lineNo ASC")
    private List<BomComponent> components = new ArrayList<>();

    public BillOfMaterials(Long parentItemId, int version, BigDecimal outputQty) {
        if (parentItemId == null) {
            throw new IllegalArgumentException("parentItemId is required");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (outputQty == null || outputQty.signum() <= 0) {
            throw new IllegalArgumentException("outputQty must be positive");
        }
        this.parentItemId = parentItemId;
        this.version = version;
        this.outputQty = outputQty;
        this.status = BomStatus.ACTIVE;
    }

    public BomComponent addComponent(Long componentItemId, BigDecimal qtyPer, BigDecimal scrapPct) {
        BomComponent component = new BomComponent(this, components.size() + 1, componentItemId, qtyPer,
                scrapPct);
        components.add(component);
        return component;
    }

    public List<BomComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }
}
