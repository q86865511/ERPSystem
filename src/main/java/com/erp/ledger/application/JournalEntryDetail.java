package com.erp.ledger.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full view of one journal entry (header + lines), including its reversal links by business entry number. */
public record JournalEntryDetail(
        Long entryNo,
        String status,
        LocalDate postingDate,
        String currencyCode,
        String memo,
        String sourceDocType,
        String sourceDocId,
        Long reversesEntryNo,
        Long reversedByEntryNo,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        List<Line> lines) {

    @Schema(name = "JournalEntryDetailLine")
    public record Line(String accountCode, String accountName, BigDecimal debit, BigDecimal credit,
                       String memo, Long partnerId) {
    }
}
