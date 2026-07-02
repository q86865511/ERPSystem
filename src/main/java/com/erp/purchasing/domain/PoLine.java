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
import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

/**
 * A purchase-order line. Tracks the ordered quantity against what has been received and billed, so
 * partial receipts/billing and GR-IR clearing can be reconciled line by line.
 */
@Entity
@Table(name = "po_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class PoLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty_ordered", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyOrdered;

    @Column(name = "qty_received", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyReceived;

    @Column(name = "qty_billed", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyBilled;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    PoLine(PurchaseOrder purchaseOrder, int lineNo, Long itemId, BigDecimal qtyOrdered,
           BigDecimal unitPrice, LocalDate expectedDeliveryDate) {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (qtyOrdered == null || qtyOrdered.signum() <= 0) {
            throw new IllegalArgumentException("qtyOrdered must be positive");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        this.purchaseOrder = purchaseOrder;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.qtyOrdered = qtyOrdered;
        this.qtyReceived = BigDecimal.ZERO;
        this.qtyBilled = BigDecimal.ZERO;
        this.unitPrice = unitPrice;
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public BigDecimal outstandingToReceive() {
        return qtyOrdered.subtract(qtyReceived);
    }

    public boolean isFullyReceived() {
        return qtyReceived.compareTo(qtyOrdered) >= 0;
    }

    void receive(BigDecimal qty) {
        this.qtyReceived = qtyReceived.add(qty);
    }

    void bill(BigDecimal qty) {
        this.qtyBilled = qtyBilled.add(qty);
    }
}
