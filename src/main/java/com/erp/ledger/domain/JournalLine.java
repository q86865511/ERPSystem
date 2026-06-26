package com.erp.ledger.domain;

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
 * One leg of a journal entry. Exactly one of {@code debit}/{@code credit} is non-zero (enforced by a
 * DB check). The {@code partnerId}/{@code productId}/{@code moId} columns are analytic dimensions that
 * later modules populate; they are unused in Phase 0.
 */
@Entity
@Table(name = "journal_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit;

    @Column(length = 500)
    private String memo;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "mo_id")
    private Long moId;

    JournalLine(JournalEntry journalEntry, int lineNo, Long accountId,
                BigDecimal debit, BigDecimal credit, String memo, Long partnerId) {
        this.journalEntry = journalEntry;
        this.lineNo = lineNo;
        this.accountId = accountId;
        this.debit = debit;
        this.credit = credit;
        this.memo = memo;
        this.partnerId = partnerId;
    }
}
