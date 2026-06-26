package com.erp.sales.domain;

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
 * A delivery line: a shipped quantity at its delivery cost (the moving-average cost at issue), linked
 * to the stock movement and journal entry it posted. {@code qtyInvoiced} tracks how much of this
 * delivery a customer invoice has cleared against Deferred-COGS.
 */
@Entity
@Table(name = "delivery_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class DeliveryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(name = "so_line_id", nullable = false)
    private Long soLineId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty_shipped", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyShipped;

    @Column(name = "qty_invoiced", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyInvoiced;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "movement_group_id")
    private UUID movementGroupId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    DeliveryLine(Delivery delivery, Long soLineId, Long itemId, BigDecimal qtyShipped,
                 BigDecimal unitCost, UUID movementGroupId, Long journalEntryId) {
        this.delivery = delivery;
        this.soLineId = soLineId;
        this.itemId = itemId;
        this.qtyShipped = qtyShipped;
        this.qtyInvoiced = BigDecimal.ZERO;
        this.unitCost = unitCost;
        this.movementGroupId = movementGroupId;
        this.journalEntryId = journalEntryId;
    }

    public BigDecimal outstandingToInvoice() {
        return qtyShipped.subtract(qtyInvoiced);
    }

    /** Records invoiced quantity against this delivery line when a customer invoice clears its COGS. */
    public void invoice(BigDecimal qty) {
        this.qtyInvoiced = qtyInvoiced.add(qty);
    }
}
