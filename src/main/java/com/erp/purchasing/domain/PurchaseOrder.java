package com.erp.purchasing.domain;

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
 * A purchase order — a commitment to a vendor. It posts nothing; goods receipts (and later vendor
 * bills) drive its status. Owns its {@link PoLine}s and tracks received/billed quantities per line.
 */
@Entity
@Table(name = "purchase_order")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false, unique = true)
    private String poNumber;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("lineNo ASC")
    private List<PoLine> lines = new ArrayList<>();

    public PurchaseOrder(String poNumber, Long partnerId, LocalDate orderDate) {
        if (poNumber == null || poNumber.isBlank()) {
            throw new IllegalArgumentException("poNumber is required");
        }
        if (partnerId == null) {
            throw new IllegalArgumentException("partnerId is required");
        }
        if (orderDate == null) {
            throw new IllegalArgumentException("orderDate is required");
        }
        this.poNumber = poNumber;
        this.partnerId = partnerId;
        this.orderDate = orderDate;
        this.status = PurchaseOrderStatus.DRAFT;
    }

    public PoLine addLine(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice) {
        return addLine(itemId, qtyOrdered, unitPrice, null);
    }

    public PoLine addLine(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice,
                          java.time.LocalDate expectedDeliveryDate) {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("can only add lines to a DRAFT order, was " + status);
        }
        PoLine line = new PoLine(this, lines.size() + 1, itemId, qtyOrdered, unitPrice,
                expectedDeliveryDate);
        lines.add(line);
        return line;
    }

    public void confirm() {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT order can be confirmed, was " + status);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("cannot confirm an order with no lines");
        }
        this.status = PurchaseOrderStatus.CONFIRMED;
    }

    public PoLine lineById(Long poLineId) {
        return lines.stream().filter(l -> l.getId().equals(poLineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "po line " + poLineId + " is not on order " + poNumber));
    }

    /** Records a receipt against a line (after it has posted) and advances the order's status. */
    public void applyReceipt(Long poLineId, BigDecimal qty) {
        if (status != PurchaseOrderStatus.CONFIRMED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("cannot receive against an order in status " + status);
        }
        PoLine line = lineById(poLineId);
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("receipt quantity must be positive");
        }
        if (qty.compareTo(line.outstandingToReceive()) > 0) {
            throw new IllegalStateException("over-receipt on line " + line.getLineNo()
                    + ": " + qty + " exceeds outstanding " + line.outstandingToReceive());
        }
        line.receive(qty);
        this.status = lines.stream().allMatch(PoLine::isFullyReceived)
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    /** Records billed quantity against a line (used when a vendor bill matches receipts). */
    public void applyBilling(Long poLineId, BigDecimal qty) {
        lineById(poLineId).bill(qty);
    }

    public List<PoLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
