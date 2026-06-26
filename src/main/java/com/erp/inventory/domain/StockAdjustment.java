package com.erp.inventory.domain;

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
import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A stock-adjustment document. {@code qtyDelta} is signed: positive is a gain (found stock), negative
 * a loss. A gain may carry a {@code unitCost} (else the item's standard cost is used); a loss is valued
 * at the current moving average. Posting runs the two-leg stock movement and the balanced journal entry
 * in one transaction, then links the resulting {@code journalEntryId} / {@code movementGroupId}.
 */
@Entity
@Table(name = "stock_adjustment")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_number", nullable = false, unique = true)
    private String adjustmentNumber;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "qty_delta", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyDelta;

    @Column(name = "unit_cost", precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockAdjustmentStatus status;

    @Column(name = "movement_group_id")
    private UUID movementGroupId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "posted_at")
    private Instant postedAt;

    public StockAdjustment(String adjustmentNumber, Long itemId, Long locationId, BigDecimal qtyDelta,
                           BigDecimal unitCost, String reason) {
        if (adjustmentNumber == null || adjustmentNumber.isBlank()) {
            throw new IllegalArgumentException("adjustmentNumber is required");
        }
        if (itemId == null || locationId == null) {
            throw new IllegalArgumentException("itemId and locationId are required");
        }
        if (qtyDelta == null || qtyDelta.signum() == 0) {
            throw new IllegalArgumentException("qtyDelta must be non-zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        this.adjustmentNumber = adjustmentNumber;
        this.itemId = itemId;
        this.locationId = locationId;
        this.qtyDelta = qtyDelta;
        this.unitCost = unitCost;
        this.reason = reason;
        this.status = StockAdjustmentStatus.DRAFT;
    }

    public void markPosted(UUID movementGroupId, Long journalEntryId, Instant postedAt) {
        if (status != StockAdjustmentStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT adjustment can be posted, was " + status);
        }
        this.movementGroupId = movementGroupId;
        this.journalEntryId = journalEntryId;
        this.postedAt = postedAt;
        this.status = StockAdjustmentStatus.POSTED;
    }
}
