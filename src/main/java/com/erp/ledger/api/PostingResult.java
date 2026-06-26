package com.erp.ledger.api;

/**
 * The published outcome of a successful post. Carries only what a cross-module caller needs — the
 * persisted entry's id (to link back from a subledger row), its gapless {@code entryNo} and status —
 * never the internal {@code JournalEntry} domain entity.
 */
public record PostingResult(Long entryId, Long entryNo, String status) {
}
