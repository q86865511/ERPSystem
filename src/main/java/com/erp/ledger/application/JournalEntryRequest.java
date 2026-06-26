package com.erp.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Command to post a journal entry. {@code journalCode} and {@code currencyCode} may be null to take
 * the defaults. The {@code sourceDocType}/{@code sourceDocId}/{@code sourceEvent} triple, when all
 * present, makes the post idempotent: the same source event posts at most one live entry.
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

    public record Line(String accountCode, BigDecimal debit, BigDecimal credit, String memo) {
    }
}
