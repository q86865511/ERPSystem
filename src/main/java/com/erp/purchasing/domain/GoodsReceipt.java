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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A goods receipt against a purchase order. Created already POSTED — each line has driven a stock
 * receipt (VENDOR → STOCK, Dr Inventory / Cr GR-IR) through the inventory posting port in the same
 * transaction. Immutable once posted.
 */
@Entity
@Table(name = "goods_receipt")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grn_number", nullable = false, unique = true)
    private String grnNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoodsReceiptStatus status;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<GrnLine> lines = new ArrayList<>();

    public GoodsReceipt(String grnNumber, Long purchaseOrderId, LocalDate postingDate) {
        if (grnNumber == null || grnNumber.isBlank()) {
            throw new IllegalArgumentException("grnNumber is required");
        }
        if (purchaseOrderId == null) {
            throw new IllegalArgumentException("purchaseOrderId is required");
        }
        if (postingDate == null) {
            throw new IllegalArgumentException("postingDate is required");
        }
        this.grnNumber = grnNumber;
        this.purchaseOrderId = purchaseOrderId;
        this.postingDate = postingDate;
        this.status = GoodsReceiptStatus.POSTED;
        this.postedAt = Instant.now();
    }

    public GrnLine addLine(Long poLineId, Long itemId, BigDecimal qtyReceived, BigDecimal unitCost,
                           UUID movementGroupId, Long journalEntryId) {
        GrnLine line = new GrnLine(this, poLineId, itemId, qtyReceived, unitCost,
                movementGroupId, journalEntryId);
        lines.add(line);
        return line;
    }

    public List<GrnLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
