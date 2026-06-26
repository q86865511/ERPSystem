package com.erp.ledger.application;

import com.erp.ledger.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /** Idempotency lookup: a source document/event posts at most one live entry. */
    Optional<JournalEntry> findBySourceDocTypeAndSourceDocIdAndSourceEvent(
            String sourceDocType, String sourceDocId, String sourceEvent);
}
