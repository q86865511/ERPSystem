package com.erp.ledger.api;

/**
 * The ledger module's published posting port. Other modules (inventory, purchasing, sales, ...) post
 * balanced journal entries through this interface only — they never reach into {@code ledger.application}
 * or import the {@code JournalEntry} domain entity. The single in-process implementation runs the post
 * synchronously, in the caller's transaction, so a document, its inventory legs and its journal entry
 * commit or roll back together.
 */
public interface LedgerPosting {

    /**
     * Posts a balanced journal entry and returns a minimal {@link PostingResult}. Honours the same
     * invariants as the internal service: the period must be OPEN, debits must equal credits, and a
     * repeated {@code (sourceDocType, sourceDocId, sourceEvent)} is idempotent (returns the existing
     * entry rather than posting a duplicate).
     */
    PostingResult post(JournalEntryRequest request, String actor);
}
