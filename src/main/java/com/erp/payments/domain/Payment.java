package com.erp.payments.domain;

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
 * A payment to (OUT) or from (IN) a partner, allocated across one or more documents. Posting it moves
 * cash and the AP/AR control account in one balanced journal entry; the allocations advance the paid
 * documents' status. Immutable once POSTED.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_number", nullable = false, unique = true)
    private String payNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentDirection direction;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PaymentAllocation> allocations = new ArrayList<>();

    public Payment(String payNumber, PaymentDirection direction, Long partnerId, BigDecimal amount,
                   LocalDate postingDate) {
        if (payNumber == null || payNumber.isBlank()) {
            throw new IllegalArgumentException("payNumber is required");
        }
        if (direction == null || partnerId == null || postingDate == null) {
            throw new IllegalArgumentException("direction, partnerId and postingDate are required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("payment amount must be positive");
        }
        this.payNumber = payNumber;
        this.direction = direction;
        this.partnerId = partnerId;
        this.amount = amount;
        this.postingDate = postingDate;
        this.status = PaymentStatus.POSTED;
        this.postedAt = Instant.now();
    }

    public PaymentAllocation addAllocation(Long documentId, BigDecimal amount) {
        PaymentAllocation allocation = new PaymentAllocation(this, documentId, amount);
        allocations.add(allocation);
        return allocation;
    }

    public void linkJournalEntry(Long journalEntryId) {
        this.journalEntryId = journalEntryId;
    }

    public List<PaymentAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }
}
