package com.erp.ledger.web;

import com.erp.ledger.domain.JournalEntry;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API view of a posted journal entry. */
public record JournalEntryResponse(
        Long id,
        Long entryNo,
        String status,
        LocalDate postingDate,
        String currencyCode,
        BigDecimal totalDebit,
        BigDecimal totalCredit) {

    public static JournalEntryResponse from(JournalEntry entry) {
        return new JournalEntryResponse(
                entry.getId(),
                entry.getEntryNo(),
                entry.getStatus().name(),
                entry.getPostingDate(),
                entry.getCurrencyCode(),
                entry.totalDebit(),
                entry.totalCredit());
    }
}
