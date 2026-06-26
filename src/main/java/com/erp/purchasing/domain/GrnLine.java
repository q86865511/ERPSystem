package com.erp.purchasing.domain;

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
 * A goods-receipt line: a received quantity at its receipt cost (the PO unit price), linked to the
 * stock movement and journal entry it posted. {@code qtyBilled} tracks how much of this receipt a
 * vendor bill has cleared against GR-IR.
 */
@Entity
@Table(name = "grn_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class GrnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @Column(name = "po_line_id", nullable = false)
    private Long poLineId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty_received", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyReceived;

    @Column(name = "qty_billed", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyBilled;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "movement_group_id")
    private UUID movementGroupId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    GrnLine(GoodsReceipt goodsReceipt, Long poLineId, Long itemId, BigDecimal qtyReceived,
            BigDecimal unitCost, UUID movementGroupId, Long journalEntryId) {
        this.goodsReceipt = goodsReceipt;
        this.poLineId = poLineId;
        this.itemId = itemId;
        this.qtyReceived = qtyReceived;
        this.qtyBilled = BigDecimal.ZERO;
        this.unitCost = unitCost;
        this.movementGroupId = movementGroupId;
        this.journalEntryId = journalEntryId;
    }

    public BigDecimal outstandingToBill() {
        return qtyReceived.subtract(qtyBilled);
    }

    void bill(BigDecimal qty) {
        this.qtyBilled = qtyBilled.add(qty);
    }
}
