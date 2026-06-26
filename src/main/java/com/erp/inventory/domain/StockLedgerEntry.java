package com.erp.inventory.domain;

import com.erp.masterdata.api.InventoryMovementType;
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
 * One immutable, append-only leg of a stock movement. Every movement writes two legs sharing a
 * {@code movementGroupId} (one per location), so on-hand and value are always the {@code SUM} of legs,
 * never edited in place. A DB trigger blocks UPDATE/DELETE. The leg on a STOCK location feeds the
 * moving average; virtual-location legs (INVENTORY_LOSS, ...) carry value for the subledger and journal
 * entry only. Quantity/cost are scale 6; {@code valueDelta} is money (scale 4) to reconcile to the GL.
 */
@Entity
@Table(name = "stock_ledger_entry")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class StockLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movement_group_id", nullable = false)
    private UUID movementGroupId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "qty_delta", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyDelta;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "value_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal valueDelta;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private InventoryMovementType movementType;

    @Column(name = "source_doc_type", nullable = false)
    private String sourceDocType;

    @Column(name = "source_doc_id", nullable = false)
    private String sourceDocId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public StockLedgerEntry(UUID movementGroupId, Long itemId, Long locationId, BigDecimal qtyDelta,
                            BigDecimal unitCost, BigDecimal valueDelta, InventoryMovementType movementType,
                            String sourceDocType, String sourceDocId, Long journalEntryId,
                            Instant postedAt) {
        this.movementGroupId = movementGroupId;
        this.itemId = itemId;
        this.locationId = locationId;
        this.qtyDelta = qtyDelta;
        this.unitCost = unitCost;
        this.valueDelta = valueDelta;
        this.movementType = movementType;
        this.sourceDocType = sourceDocType;
        this.sourceDocId = sourceDocId;
        this.journalEntryId = journalEntryId;
        this.postedAt = postedAt;
    }
}
