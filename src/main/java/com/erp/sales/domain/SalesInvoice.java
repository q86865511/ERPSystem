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

import static lombok.AccessLevel.PROTECTED;

/**
 * A customer invoice against a sales order. Posting it recognises revenue + Output VAT against the AR
 * control account, and recognises COGS by clearing the Deferred-COGS parked at delivery. Receipt
 * allocation later advances it to PARTIALLY_PAID / PAID. The open balance ({@code gross − amountReceived})
 * is the authoritative AR subledger figure.
 */
@Entity
@Table(name = "sales_invoice")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class SalesInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "goods_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal goodsAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "cogs_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal cogsAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "amount_received", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountReceived;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesInvoiceStatus status;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @OneToMany(mappedBy = "salesInvoice", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    public SalesInvoice(String invoiceNumber, Long salesOrderId, Long partnerId, LocalDate postingDate) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("invoiceNumber is required");
        }
        if (salesOrderId == null || partnerId == null) {
            throw new IllegalArgumentException("salesOrderId and partnerId are required");
        }
        if (postingDate == null) {
            throw new IllegalArgumentException("postingDate is required");
        }
        this.invoiceNumber = invoiceNumber;
        this.salesOrderId = salesOrderId;
        this.partnerId = partnerId;
        this.postingDate = postingDate;
        this.goodsAmount = BigDecimal.ZERO;
        this.vatAmount = BigDecimal.ZERO;
        this.cogsAmount = BigDecimal.ZERO;
        this.grossAmount = BigDecimal.ZERO;
        this.amountReceived = BigDecimal.ZERO;
        this.status = SalesInvoiceStatus.DRAFT;
    }

    public InvoiceLine addLine(Long soLineId, Long itemId, BigDecimal qty, BigDecimal unitPrice,
                               BigDecimal lineNet, BigDecimal lineVat, BigDecimal lineCogs) {
        InvoiceLine line = new InvoiceLine(this, soLineId, itemId, qty, unitPrice, lineNet, lineVat,
                lineCogs);
        lines.add(line);
        return line;
    }

    public void markPosted(BigDecimal goodsAmount, BigDecimal vatAmount, BigDecimal cogsAmount,
                           BigDecimal grossAmount, Long journalEntryId) {
        if (status != SalesInvoiceStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT invoice can be posted, was " + status);
        }
        this.goodsAmount = goodsAmount;
        this.vatAmount = vatAmount;
        this.cogsAmount = cogsAmount;
        this.grossAmount = grossAmount;
        this.journalEntryId = journalEntryId;
        this.postedAt = Instant.now();
        this.status = SalesInvoiceStatus.POSTED;
    }

    public BigDecimal openBalance() {
        return grossAmount.subtract(amountReceived);
    }

    /** Allocates a customer receipt to this invoice, advancing its status. */
    public void applyReceipt(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("receipt amount must be positive");
        }
        if (amount.compareTo(openBalance()) > 0) {
            throw new IllegalStateException("receipt " + amount + " exceeds open balance "
                    + openBalance() + " on invoice " + invoiceNumber);
        }
        this.amountReceived = amountReceived.add(amount);
        this.status = openBalance().signum() == 0
                ? SalesInvoiceStatus.PAID
                : SalesInvoiceStatus.PARTIALLY_PAID;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
