package com.erp.sales.domain;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static lombok.AccessLevel.PROTECTED;

/**
 * A sales order — a commitment to a customer. It posts nothing; deliveries (and later customer
 * invoices) drive its status. Owns its {@link SoLine}s and tracks shipped/invoiced quantities per line.
 */
@Entity
@Table(name = "sales_order")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "so_number", nullable = false, unique = true)
    private String soNumber;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesOrderStatus status;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("lineNo ASC")
    private List<SoLine> lines = new ArrayList<>();

    public SalesOrder(String soNumber, Long partnerId, LocalDate orderDate) {
        if (soNumber == null || soNumber.isBlank()) {
            throw new IllegalArgumentException("soNumber is required");
        }
        if (partnerId == null) {
            throw new IllegalArgumentException("partnerId is required");
        }
        if (orderDate == null) {
            throw new IllegalArgumentException("orderDate is required");
        }
        this.soNumber = soNumber;
        this.partnerId = partnerId;
        this.orderDate = orderDate;
        this.status = SalesOrderStatus.DRAFT;
    }

    public SoLine addLine(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice) {
        if (status != SalesOrderStatus.DRAFT) {
            throw new IllegalStateException("can only add lines to a DRAFT order, was " + status);
        }
        SoLine line = new SoLine(this, lines.size() + 1, itemId, qtyOrdered, unitPrice);
        lines.add(line);
        return line;
    }

    public void confirm() {
        if (status != SalesOrderStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT order can be confirmed, was " + status);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("cannot confirm an order with no lines");
        }
        this.status = SalesOrderStatus.CONFIRMED;
    }

    public SoLine lineById(Long soLineId) {
        return lines.stream().filter(l -> l.getId().equals(soLineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "so line " + soLineId + " is not on order " + soNumber));
    }

    /** Records a delivery against a line (after it has posted) and advances the order's status. */
    public void applyDelivery(Long soLineId, BigDecimal qty) {
        if (status != SalesOrderStatus.CONFIRMED && status != SalesOrderStatus.PARTIALLY_SHIPPED) {
            throw new IllegalStateException("cannot deliver against an order in status " + status);
        }
        SoLine line = lineById(soLineId);
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("delivery quantity must be positive");
        }
        if (qty.compareTo(line.outstandingToShip()) > 0) {
            throw new IllegalStateException("over-delivery on line " + line.getLineNo()
                    + ": " + qty + " exceeds outstanding " + line.outstandingToShip());
        }
        line.ship(qty);
        this.status = lines.stream().allMatch(SoLine::isFullyShipped)
                ? SalesOrderStatus.SHIPPED
                : SalesOrderStatus.PARTIALLY_SHIPPED;
    }

    /** Records invoiced quantity against a line (used when a customer invoice matches deliveries). */
    public void applyInvoicing(Long soLineId, BigDecimal qty) {
        lineById(soLineId).invoice(qty);
    }

    public List<SoLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
