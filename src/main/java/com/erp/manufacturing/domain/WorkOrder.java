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
 * A work order to produce a finished good from a BOM. It snapshots the BOM's components at release
 * (frozen), consumes them into WIP at issue (Dr WIP / Cr component inventory) and produces finished
 * goods at completion (Dr Finished Goods / Cr WIP at rolled cost). WIP nets to zero once it completes.
 */
@Entity
@Table(name = "work_order")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wo_number", nullable = false, unique = true)
    private String woNumber;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "qty_to_produce", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyToProduce;

    @Column(name = "qty_produced", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyProduced;

    @Column(name = "wip_location_id")
    private Long wipLocationId;

    @Column(name = "total_component_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalComponentCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("lineNo ASC")
    private List<WorkOrderComponent> components = new ArrayList<>();

    public WorkOrder(String woNumber, Long itemId, Long bomId, BigDecimal qtyToProduce) {
        if (woNumber == null || woNumber.isBlank()) {
            throw new IllegalArgumentException("woNumber is required");
        }
        if (itemId == null || bomId == null) {
            throw new IllegalArgumentException("itemId and bomId are required");
        }
        if (qtyToProduce == null || qtyToProduce.signum() <= 0) {
            throw new IllegalArgumentException("qtyToProduce must be positive");
        }
        this.woNumber = woNumber;
        this.itemId = itemId;
        this.bomId = bomId;
        this.qtyToProduce = qtyToProduce;
        this.qtyProduced = BigDecimal.ZERO;
        this.totalComponentCost = BigDecimal.ZERO;
        this.status = WorkOrderStatus.DRAFT;
    }

    /** Snapshots a BOM component as a frozen planned line. Only allowed while DRAFT. */
    public WorkOrderComponent addComponent(Long componentItemId, BigDecimal plannedQty) {
        if (status != WorkOrderStatus.DRAFT) {
            throw new IllegalStateException("can only add components to a DRAFT work order, was " + status);
        }
        WorkOrderComponent component = new WorkOrderComponent(this, components.size() + 1,
                componentItemId, plannedQty);
        components.add(component);
        return component;
    }

    public void markReleased() {
        if (status != WorkOrderStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT work order can be released, was " + status);
        }
        if (components.isEmpty()) {
            throw new IllegalStateException("cannot release a work order with no components");
        }
        this.status = WorkOrderStatus.RELEASED;
    }

    /** Begins consumption: marks IN_PROGRESS and pins the WIP location used for the postings. */
    public void beginIssue(Long wipLocationId) {
        if (status != WorkOrderStatus.RELEASED) {
            throw new IllegalStateException("only a RELEASED work order can be issued, was " + status);
        }
        this.wipLocationId = wipLocationId;
        this.status = WorkOrderStatus.IN_PROGRESS;
    }

    public void addComponentCost(BigDecimal value) {
        this.totalComponentCost = totalComponentCost.add(value);
    }

    public void markDone(BigDecimal qtyProduced) {
        if (status != WorkOrderStatus.IN_PROGRESS) {
            throw new IllegalStateException("only an IN_PROGRESS work order can complete, was " + status);
        }
        if (qtyProduced == null || qtyProduced.signum() <= 0) {
            throw new IllegalArgumentException("qtyProduced must be positive");
        }
        this.qtyProduced = qtyProduced;
        this.status = WorkOrderStatus.DONE;
    }

    public void markCancelled() {
        if (status != WorkOrderStatus.RELEASED && status != WorkOrderStatus.IN_PROGRESS) {
            throw new IllegalStateException("only a RELEASED/IN_PROGRESS work order can be cancelled, was "
                    + status);
        }
        this.status = WorkOrderStatus.CANCELLED;
    }

    public List<WorkOrderComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }
}
