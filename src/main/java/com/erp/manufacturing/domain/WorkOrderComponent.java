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
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A work-order component: the BOM line snapshotted at release ({@code plannedQty}, frozen) and what was
 * actually consumed into WIP at issue ({@code consumedQty}/{@code consumedValue}), linked to the stock
 * movement and journal entry it posted.
 */
@Entity
@Table(name = "work_order_component")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class WorkOrderComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "component_item_id", nullable = false)
    private Long componentItemId;

    @Column(name = "planned_qty", nullable = false, precision = 19, scale = 6)
    private BigDecimal plannedQty;

    @Column(name = "consumed_qty", nullable = false, precision = 19, scale = 6)
    private BigDecimal consumedQty;

    @Column(name = "consumed_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal consumedValue;

    @Column(name = "movement_group_id")
    private UUID movementGroupId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    WorkOrderComponent(WorkOrder workOrder, int lineNo, Long componentItemId, BigDecimal plannedQty) {
        if (componentItemId == null) {
            throw new IllegalArgumentException("componentItemId is required");
        }
        if (plannedQty == null || plannedQty.signum() <= 0) {
            throw new IllegalArgumentException("plannedQty must be positive");
        }
        this.workOrder = workOrder;
        this.lineNo = lineNo;
        this.componentItemId = componentItemId;
        this.plannedQty = plannedQty;
        this.consumedQty = BigDecimal.ZERO;
        this.consumedValue = BigDecimal.ZERO;
    }

    /** Records the consumption of this component into WIP. */
    public void consume(BigDecimal qty, BigDecimal value, UUID movementGroupId, Long journalEntryId) {
        this.consumedQty = consumedQty.add(qty);
        this.consumedValue = consumedValue.add(value);
        this.movementGroupId = movementGroupId;
        this.journalEntryId = journalEntryId;
    }
}
