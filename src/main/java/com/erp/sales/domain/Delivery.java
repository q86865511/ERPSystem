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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A delivery against a sales order. Created already POSTED — each line has driven a stock issue
 * (STOCK → CUSTOMER, Dr Deferred-COGS / Cr Finished Goods) through the inventory posting port in the
 * same transaction. Immutable once posted.
 */
@Entity
@Table(name = "delivery")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_number", nullable = false, unique = true)
    private String deliveryNumber;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<DeliveryLine> lines = new ArrayList<>();

    public Delivery(String deliveryNumber, Long salesOrderId, LocalDate postingDate) {
        if (deliveryNumber == null || deliveryNumber.isBlank()) {
            throw new IllegalArgumentException("deliveryNumber is required");
        }
        if (salesOrderId == null) {
            throw new IllegalArgumentException("salesOrderId is required");
        }
        if (postingDate == null) {
            throw new IllegalArgumentException("postingDate is required");
        }
        this.deliveryNumber = deliveryNumber;
        this.salesOrderId = salesOrderId;
        this.postingDate = postingDate;
        this.status = DeliveryStatus.POSTED;
        this.postedAt = Instant.now();
    }

    public DeliveryLine addLine(Long soLineId, Long itemId, BigDecimal qtyShipped, BigDecimal unitCost,
                                UUID movementGroupId, Long journalEntryId) {
        DeliveryLine line = new DeliveryLine(this, soLineId, itemId, qtyShipped, unitCost,
                movementGroupId, journalEntryId);
        lines.add(line);
        return line;
    }

    public List<DeliveryLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
