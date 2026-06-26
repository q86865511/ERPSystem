package com.erp.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * A row-locked counter used for gapless numbering. The posting service takes a pessimistic write lock
 * on the row for a scope, reads {@link #nextValue}, then advances it — so concurrent posters serialize
 * only on the counter, not the whole table. Used for {@code JournalEntry.entry_no}.
 */
@Entity
@Table(name = "number_sequence")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class NumberSequence {

    @Id
    private String scope;

    @Column(nullable = false)
    private String prefix;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Column(nullable = false)
    private int padding;

    /** Returns the current value and advances the counter by one. */
    public long take() {
        long current = nextValue;
        nextValue = current + 1;
        return current;
    }

    /** Formats a value with this sequence's prefix and zero-padding, e.g. {@code JE-000042}. */
    public String format(long value) {
        return prefix + "-" + String.format("%0" + padding + "d", value);
    }
}
