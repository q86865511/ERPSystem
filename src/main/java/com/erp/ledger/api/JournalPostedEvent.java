package com.erp.ledger.api;

import java.time.LocalDate;

/**
 * Published after a journal entry posts. Since {@code LedgerPostingService} is the single posting entry
 * point, listening to this captures every financial action (receipts, bills, invoices, payments,
 * manufacturing, year-end, manual entries). Carries only primitives so the audit module depends on this
 * api type, not ledger internals.
 */
public record JournalPostedEvent(
        Long entryId,
        Long entryNo,
        LocalDate postingDate,
        String sourceDocType,
        String sourceDocId,
        String memo,
        String actor) {}
