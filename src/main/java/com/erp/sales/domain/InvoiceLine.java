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
 * A customer-invoice line: an invoiced quantity at the sales price, with its net and VAT amounts and the
 * COGS it recognised (the delivery cost it cleared from Deferred-COGS). All money fields are scale 4.
 */
@Entity
@Table(name = "invoice_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", nullable = false)
    private SalesInvoice salesInvoice;

    @Column(name = "so_line_id", nullable = false)
    private Long soLineId;

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

    @Column(name = "line_cogs", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineCogs;

    InvoiceLine(SalesInvoice salesInvoice, Long soLineId, Long itemId, BigDecimal qty,
                BigDecimal unitPrice, BigDecimal lineNet, BigDecimal lineVat, BigDecimal lineCogs) {
        this.salesInvoice = salesInvoice;
        this.soLineId = soLineId;
        this.itemId = itemId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.lineNet = lineNet;
        this.lineVat = lineVat;
        this.lineCogs = lineCogs;
    }
}
