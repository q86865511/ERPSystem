package com.erp.ledger.application;

import com.erp.ledger.domain.Account;
import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.Journal;
import com.erp.ledger.domain.JournalEntry;
import com.erp.ledger.domain.NumberSequence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The single entry point through which every balanced journal entry is posted. No other code writes
 * {@code journal_entry}/{@code journal_line} rows. The whole post — period resolution, gapless
 * numbering, line insertion and the balance check — happens in one transaction; if anything fails,
 * nothing is posted.
 */
@Service
public class LedgerPostingService {

    static final String DEFAULT_JOURNAL = "GEN";
    static final String DEFAULT_CURRENCY = "TWD";
    static final String ENTRY_NO_SCOPE = "JOURNAL_ENTRY";

    private final AccountRepository accountRepository;
    private final JournalRepository journalRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberSequenceRepository numberSequenceRepository;

    public LedgerPostingService(AccountRepository accountRepository,
                                JournalRepository journalRepository,
                                FiscalPeriodRepository fiscalPeriodRepository,
                                JournalEntryRepository journalEntryRepository,
                                NumberSequenceRepository numberSequenceRepository) {
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberSequenceRepository = numberSequenceRepository;
    }

    @Transactional
    public JournalEntry post(JournalEntryRequest request, String actor) {
        validateStructure(request);

        if (hasSourceKey(request)) {
            var existing = journalEntryRepository.findBySourceDocTypeAndSourceDocIdAndSourceEvent(
                    request.sourceDocType(), request.sourceDocId(), request.sourceEvent());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String currency = request.currencyCode() != null ? request.currencyCode() : DEFAULT_CURRENCY;

        FiscalPeriod period = fiscalPeriodRepository.findByDate(request.postingDate())
                .filter(FiscalPeriod::isOpen)
                .orElseThrow(() -> new PeriodNotOpenException(request.postingDate()));

        String journalCode = request.journalCode() != null ? request.journalCode() : DEFAULT_JOURNAL;
        Journal journal = journalRepository.findByCode(journalCode)
                .orElseThrow(() -> new InvalidLedgerRequestException("unknown journal: " + journalCode));

        JournalEntry entry = new JournalEntry(journal.getId(), period.getId(), request.postingDate(),
                request.memo(), currency, request.sourceDocType(), request.sourceDocId(),
                request.sourceEvent(), actor);

        for (JournalEntryRequest.Line line : request.lines()) {
            Account account = accountRepository.findByCode(line.accountCode())
                    .filter(Account::isPostable)
                    .orElseThrow(() -> new AccountNotFoundException(line.accountCode()));
            BigDecimal debit = line.debit() != null ? line.debit() : BigDecimal.ZERO;
            BigDecimal credit = line.credit() != null ? line.credit() : BigDecimal.ZERO;
            try {
                entry.addLine(account.getId(), debit, credit, line.memo());
            } catch (IllegalArgumentException ex) {
                throw new InvalidLedgerRequestException(ex.getMessage());
            }
        }

        if (!entry.isBalanced()) {
            throw new UnbalancedEntryException(entry.totalDebit(), entry.totalCredit());
        }

        NumberSequence sequence = numberSequenceRepository.lockByScope(ENTRY_NO_SCOPE)
                .orElseThrow(() -> new IllegalStateException("missing number sequence: " + ENTRY_NO_SCOPE));
        long entryNo = sequence.take();

        entry.markPosted(entryNo, actor, Instant.now());
        // saveAndFlush surfaces DB-level invariant violations (balance, immutability, idempotency)
        // inside this transaction rather than at an opaque commit boundary.
        return journalEntryRepository.saveAndFlush(entry);
    }

    private void validateStructure(JournalEntryRequest request) {
        if (request.postingDate() == null) {
            throw new InvalidLedgerRequestException("postingDate is required");
        }
        List<JournalEntryRequest.Line> lines = request.lines();
        if (lines == null || lines.size() < 2) {
            throw new InvalidLedgerRequestException("a journal entry needs at least two lines");
        }
    }

    private boolean hasSourceKey(JournalEntryRequest request) {
        return request.sourceDocType() != null
                && request.sourceDocId() != null
                && request.sourceEvent() != null;
    }
}
