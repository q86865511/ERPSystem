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

import static lombok.AccessLevel.PROTECTED;

/**
 * A sales-order line. Tracks the ordered quantity against what has been shipped and invoiced, so
 * partial deliveries/invoicing and Deferred-COGS clearing can be reconciled line by line.
 * {@code unitPrice} is the sales price (revenue is recognised at this price, independent of cost).
 */
@Entity
@Table(name = "so_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class SoLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty_ordered", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyOrdered;

    @Column(name = "qty_shipped", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyShipped;

    @Column(name = "qty_invoiced", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtyInvoiced;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitPrice;

    SoLine(SalesOrder salesOrder, int lineNo, Long itemId, BigDecimal qtyOrdered,
           BigDecimal unitPrice) {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (qtyOrdered == null || qtyOrdered.signum() <= 0) {
            throw new IllegalArgumentException("qtyOrdered must be positive");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        this.salesOrder = salesOrder;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.qtyOrdered = qtyOrdered;
        this.qtyShipped = BigDecimal.ZERO;
        this.qtyInvoiced = BigDecimal.ZERO;
        this.unitPrice = unitPrice;
    }

    public BigDecimal outstandingToShip() {
        return qtyOrdered.subtract(qtyShipped);
    }

    public boolean isFullyShipped() {
        return qtyShipped.compareTo(qtyOrdered) >= 0;
    }

    void ship(BigDecimal qty) {
        this.qtyShipped = qtyShipped.add(qty);
    }

    void invoice(BigDecimal qty) {
        this.qtyInvoiced = qtyInvoiced.add(qty);
    }
}
