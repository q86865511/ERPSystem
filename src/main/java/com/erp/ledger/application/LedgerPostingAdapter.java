package com.erp.ledger.application;

import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import com.erp.ledger.domain.JournalEntry;
import org.springframework.stereotype.Component;

/**
 * Adapts the internal {@link LedgerPostingService} to the published {@link LedgerPosting} port. The
 * service keeps returning the rich {@link JournalEntry} aggregate for same-module callers (the web
 * layer needs its totals); cross-module callers get only the minimal {@link PostingResult}. The call
 * runs in the caller's transaction, so the delegate's work commits or rolls back with it.
 */
@Component
public class LedgerPostingAdapter implements LedgerPosting {

    private final LedgerPostingService postingService;

    public LedgerPostingAdapter(LedgerPostingService postingService) {
        this.postingService = postingService;
    }

    @Override
    public PostingResult post(JournalEntryRequest request, String actor) {
        JournalEntry entry = postingService.post(request, actor);
        return new PostingResult(entry.getId(), entry.getEntryNo(), entry.getStatus().name());
    }
}
