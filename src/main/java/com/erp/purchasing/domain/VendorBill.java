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

import static lombok.AccessLevel.PROTECTED;

/**
 * A vendor (supplier) bill against a purchase order. Posting it clears GR-IR for the matched receipts,
 * records Input VAT and the accounts-payable credit, and (for a price variance) revalues inventory.
 * Payment allocation later advances it to PARTIALLY_PAID / PAID. The open balance ({@code gross −
 * amountPaid}) is the authoritative AP subledger figure.
 */
@Entity
@Table(name = "vendor_bill")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class VendorBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_number", nullable = false, unique = true)
    private String billNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "goods_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal goodsAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorBillStatus status;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @OneToMany(mappedBy = "vendorBill", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<BillLine> lines = new ArrayList<>();

    public VendorBill(String billNumber, Long purchaseOrderId, Long partnerId, LocalDate postingDate) {
        if (billNumber == null || billNumber.isBlank()) {
            throw new IllegalArgumentException("billNumber is required");
        }
        if (purchaseOrderId == null || partnerId == null) {
            throw new IllegalArgumentException("purchaseOrderId and partnerId are required");
        }
        if (postingDate == null) {
            throw new IllegalArgumentException("postingDate is required");
        }
        this.billNumber = billNumber;
        this.purchaseOrderId = purchaseOrderId;
        this.partnerId = partnerId;
        this.postingDate = postingDate;
        this.goodsAmount = BigDecimal.ZERO;
        this.vatAmount = BigDecimal.ZERO;
        this.grossAmount = BigDecimal.ZERO;
        this.amountPaid = BigDecimal.ZERO;
        this.status = VendorBillStatus.DRAFT;
    }

    public BillLine addLine(Long poLineId, Long itemId, BigDecimal qty, BigDecimal unitPrice,
                            BigDecimal lineNet, BigDecimal lineVat, BillMatchStatus matchStatus) {
        BillLine line = new BillLine(this, poLineId, itemId, qty, unitPrice, lineNet, lineVat,
                matchStatus);
        lines.add(line);
        return line;
    }

    public void markPosted(BigDecimal goodsAmount, BigDecimal vatAmount, BigDecimal grossAmount,
                           Long journalEntryId) {
        if (status != VendorBillStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT bill can be posted, was " + status);
        }
        this.goodsAmount = goodsAmount;
        this.vatAmount = vatAmount;
        this.grossAmount = grossAmount;
        this.journalEntryId = journalEntryId;
        this.postedAt = Instant.now();
        this.status = VendorBillStatus.POSTED;
    }

    public BigDecimal openBalance() {
        return grossAmount.subtract(amountPaid);
    }

    /** Allocates a payment to this bill, advancing its status. */
    public void applyPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("payment amount must be positive");
        }
        if (amount.compareTo(openBalance()) > 0) {
            throw new IllegalStateException("payment " + amount + " exceeds open balance "
                    + openBalance() + " on bill " + billNumber);
        }
        this.amountPaid = amountPaid.add(amount);
        this.status = openBalance().signum() == 0
                ? VendorBillStatus.PAID
                : VendorBillStatus.PARTIALLY_PAID;
    }

    public List<BillLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
