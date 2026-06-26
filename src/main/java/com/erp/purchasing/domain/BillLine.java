package com.erp.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A vendor-bill line: a billed quantity at the bill price, with its net and VAT amounts (money, scale
 * 4) and the three-way match outcome. It clears the GR-IR credit of the receipts it matched.
 */
@Entity
@Table(name = "bill_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BillLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_bill_id", nullable = false)
    private VendorBill vendorBill;

    @Column(name = "po_line_id", nullable = false)
    private Long poLineId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "line_net", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineNet;

    @Column(name = "line_vat", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineVat;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private BillMatchStatus matchStatus;

    BillLine(VendorBill vendorBill, Long poLineId, Long itemId, BigDecimal qty, BigDecimal unitPrice,
             BigDecimal lineNet, BigDecimal lineVat, BillMatchStatus matchStatus) {
        this.vendorBill = vendorBill;
        this.poLineId = poLineId;
        this.itemId = itemId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.lineNet = lineNet;
        this.lineVat = lineVat;
        this.matchStatus = matchStatus;
    }
}
