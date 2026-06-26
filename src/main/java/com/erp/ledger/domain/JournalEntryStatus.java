package com.erp.ledger.domain;

/**
 * Lifecycle of a journal entry. POSTED entries are immutable accounting facts; corrections are made
 * by a reversing entry (REVERSED), never by editing. VOID is reserved for cancelling a draft.
 */
public enum JournalEntryStatus {
    DRAFT,
    POSTED,
    REVERSED,
    VOID
}
