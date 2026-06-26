package com.erp.ledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * The aggregate root of double-entry accounting: a balanced set of {@link JournalLine}s.
 *
 * <p>An entry is built as {@link JournalEntryStatus#DRAFT}, has its lines added, and is then
 * {@linkplain #markPosted posted} once it balances. Posting is the only point at which an
 * {@code entry_no} is assigned. Once {@link JournalEntryStatus#POSTED}, the entry is an immutable
 * accounting fact — corrections are made by a reversing entry, never by editing (also enforced by DB
 * triggers).
 */
@Entity
@Table(name = "journal_entry")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Gapless number, assigned only at POST time. Null while DRAFT. */
    @Column(name = "entry_no")
    private Long entryNo;

    @Column(name = "journal_id", nullable = false)
    private Long journalId;

    @Column(name = "fiscal_period_id", nullable = false)
    private Long fiscalPeriodId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(length = 500)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalEntryStatus status;

    @Column(name = "source_doc_type")
    private String sourceDocType;

    @Column(name = "source_doc_id")
    private String sourceDocId;

    @Column(name = "source_event")
    private String sourceEvent;

    @Column(name = "reverses_entry_id")
    private Long reversesEntryId;

    @Column(name = "reversed_by_entry_id")
    private Long reversedByEntryId;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private String postedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<JournalLine> lines = new ArrayList<>();

    public JournalEntry(Long journalId, Long fiscalPeriodId, LocalDate postingDate, String memo,
                        String currencyCode, String sourceDocType, String sourceDocId, String sourceEvent,
                        String createdBy) {
        this.journalId = journalId;
        this.fiscalPeriodId = fiscalPeriodId;
        this.postingDate = postingDate;
        this.memo = memo;
        this.currencyCode = currencyCode;
        this.sourceDocType = sourceDocType;
        this.sourceDocId = sourceDocId;
        this.sourceEvent = sourceEvent;
        this.createdBy = createdBy;
        this.status = JournalEntryStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    /** Adds a debit/credit line; exactly one side must be strictly positive. */
    public JournalLine addLine(Long accountId, BigDecimal debit, BigDecimal credit, String memo) {
        if (debit == null || credit == null) {
            throw new IllegalArgumentException("debit and credit must not be null");
        }
        if (debit.signum() < 0 || credit.signum() < 0) {
            throw new IllegalArgumentException("debit and credit must be non-negative");
        }
        boolean debitSide = debit.signum() > 0;
        boolean creditSide = credit.signum() > 0;
        if (debitSide == creditSide) {
            throw new IllegalArgumentException("exactly one of debit/credit must be non-zero");
        }
        JournalLine line = new JournalLine(this, lines.size() + 1, accountId, debit, credit, memo);
        lines.add(line);
        return line;
    }

    public BigDecimal totalDebit() {
        return lines.stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isBalanced() {
        return totalDebit().compareTo(totalCredit()) == 0 && totalDebit().signum() > 0;
    }

    /** Transitions a balanced DRAFT entry to POSTED, stamping its number and audit fields. */
    public void markPosted(long entryNo, String postedBy, Instant postedAt) {
        if (status != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException("only a DRAFT entry can be posted, was " + status);
        }
        if (!isBalanced()) {
            throw new IllegalStateException("cannot post an unbalanced entry");
        }
        this.entryNo = entryNo;
        this.postedBy = postedBy;
        this.postedAt = postedAt;
        this.status = JournalEntryStatus.POSTED;
    }

    public List<JournalLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
