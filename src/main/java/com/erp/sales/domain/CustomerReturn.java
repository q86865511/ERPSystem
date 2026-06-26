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
 * A customer return against a posted invoice. Created already POSTED — each line has driven a stock
 * return (CUSTOMER → STOCK, Dr Finished Goods / Cr Deferred-COGS) through the inventory port, and a
 * single credit note reverses revenue + Output VAT + AR and un-recognises COGS. Immutable once posted.
 */
@Entity
@Table(name = "customer_return")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class CustomerReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_number", nullable = false, unique = true)
    private String returnNumber;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerReturnStatus status;

    @Column(name = "credit_note_journal_entry_id")
    private Long creditNoteJournalEntryId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "customerReturn", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<CustomerReturnLine> lines = new ArrayList<>();

    public CustomerReturn(String returnNumber, Long salesInvoiceId, Long salesOrderId, Long partnerId,
                          LocalDate postingDate) {
        if (returnNumber == null || returnNumber.isBlank()) {
            throw new IllegalArgumentException("returnNumber is required");
        }
        if (salesInvoiceId == null || salesOrderId == null || partnerId == null) {
            throw new IllegalArgumentException("salesInvoiceId, salesOrderId and partnerId are required");
        }
        if (postingDate == null) {
            throw new IllegalArgumentException("postingDate is required");
        }
        this.returnNumber = returnNumber;
        this.salesInvoiceId = salesInvoiceId;
        this.salesOrderId = salesOrderId;
        this.partnerId = partnerId;
        this.postingDate = postingDate;
        this.goodsAmount = BigDecimal.ZERO;
        this.vatAmount = BigDecimal.ZERO;
        this.cogsAmount = BigDecimal.ZERO;
        this.grossAmount = BigDecimal.ZERO;
        this.status = CustomerReturnStatus.POSTED;
        this.postedAt = Instant.now();
    }

    public CustomerReturnLine addLine(Long soLineId, Long itemId, BigDecimal qty, BigDecimal lineNet,
                                      BigDecimal lineVat, BigDecimal lineCogs, UUID movementGroupId,
                                      Long stockJournalEntryId) {
        CustomerReturnLine line = new CustomerReturnLine(this, soLineId, itemId, qty, lineNet, lineVat,
                lineCogs, movementGroupId, stockJournalEntryId);
        lines.add(line);
        return line;
    }

    public void finalise(BigDecimal goodsAmount, BigDecimal vatAmount, BigDecimal cogsAmount,
                         BigDecimal grossAmount, Long creditNoteJournalEntryId) {
        this.goodsAmount = goodsAmount;
        this.vatAmount = vatAmount;
        this.cogsAmount = cogsAmount;
        this.grossAmount = grossAmount;
        this.creditNoteJournalEntryId = creditNoteJournalEntryId;
    }

    public List<CustomerReturnLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
