package com.erp.payments.domain;

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
 * Allocates part of a payment to a document. {@code documentId} is the vendor bill (direction OUT) or,
 * later, the customer invoice (IN) — referenced by value, not a hard FK, so the same machinery serves
 * both sides.
 */
@Entity
@Table(name = "payment_allocation")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    PaymentAllocation(Payment payment, Long documentId, BigDecimal amount) {
        this.payment = payment;
        this.documentId = documentId;
        this.amount = amount;
    }
}
