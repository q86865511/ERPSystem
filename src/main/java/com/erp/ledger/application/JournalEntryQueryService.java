package com.erp.ledger.application;

import com.erp.ledger.domain.Account;
import com.erp.ledger.domain.JournalEntry;
import com.erp.ledger.domain.JournalLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Read-side: fetch a single journal entry with its lines (account codes/names + reversal links resolved). */
@Service
public class JournalEntryQueryService {

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;

    public JournalEntryQueryService(JournalEntryRepository entries, AccountRepository accounts) {
        this.entries = entries;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public JournalEntryDetail findDetail(Long entryNo) {
        JournalEntry e = entries.findByEntryNo(entryNo)
                .orElseThrow(() -> new EntryNotFoundException(entryNo));

        List<JournalEntryDetail.Line> lines = new ArrayList<>();
        for (JournalLine l : e.getLines()) {
            Account a = accounts.findById(l.getAccountId()).orElse(null);
            lines.add(new JournalEntryDetail.Line(
                    a != null ? a.getCode() : String.valueOf(l.getAccountId()),
                    a != null ? a.getName() : null,
                    l.getDebit(), l.getCredit(), l.getMemo(), l.getPartnerId()));
        }

        return new JournalEntryDetail(
                e.getEntryNo(),
                e.getStatus().name(),
                e.getPostingDate(),
                e.getCurrencyCode(),
                e.getMemo(),
                e.getSourceDocType(),
                e.getSourceDocId(),
                entryNoOf(e.getReversesEntryId()),
                entryNoOf(e.getReversedByEntryId()),
                e.totalDebit(),
                e.totalCredit(),
                lines);
    }

    private Long entryNoOf(Long entryId) {
        return entryId == null ? null : entries.findById(entryId).map(JournalEntry::getEntryNo).orElse(null);
    }
}
