package com.erp.ledger.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Command to post a journal entry. This is the ledger module's published request type: other modules
 * build it and hand it to {@link LedgerPosting#post}. {@code journalCode} and {@code currencyCode} may
 * be null to take the defaults. The {@code sourceDocType}/{@code sourceDocId}/{@code sourceEvent}
 * triple, when all present, makes the post idempotent: the same source event posts at most one live
 * entry.
 */
public record JournalEntryRequest(
        String journalCode,
        LocalDate postingDate,
        String memo,
        String currencyCode,
        String sourceDocType,
        String sourceDocId,
        String sourceEvent,
        List<Line> lines) {

    /**
     * One leg. {@code partnerId} is an optional analytic dimension (the vendor/customer the line
     * relates to, e.g. on an AP or GR-IR line); the four-argument form leaves it null so existing
     * callers are unaffected.
     */
    public record Line(String accountCode, BigDecimal debit, BigDecimal credit, String memo,
                       Long partnerId) {

        public Line(String accountCode, BigDecimal debit, BigDecimal credit, String memo) {
            this(accountCode, debit, credit, memo, null);
        }
    }
}
