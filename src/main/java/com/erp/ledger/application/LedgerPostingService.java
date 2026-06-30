package com.erp.ledger.application;

import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalPostedEvent;
import com.erp.ledger.domain.Account;
import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.Journal;
import com.erp.ledger.domain.JournalEntry;
import com.erp.ledger.domain.JournalEntryStatus;
import com.erp.ledger.domain.JournalLine;
import com.erp.ledger.domain.NumberSequence;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final ApplicationEventPublisher events;

    public LedgerPostingService(AccountRepository accountRepository,
                                JournalRepository journalRepository,
                                FiscalPeriodRepository fiscalPeriodRepository,
                                JournalEntryRepository journalEntryRepository,
                                NumberSequenceRepository numberSequenceRepository,
                                ApplicationEventPublisher events) {
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberSequenceRepository = numberSequenceRepository;
        this.events = events;
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
                entry.addLine(account.getId(), debit, credit, line.memo(), line.partnerId());
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
        JournalEntry saved = journalEntryRepository.saveAndFlush(entry);
        // Audit trail: published in-transaction, recorded by the audit listener after this commits.
        events.publishEvent(new JournalPostedEvent(saved.getId(), saved.getEntryNo(),
                saved.getPostingDate(), saved.getSourceDocType(), saved.getSourceDocId(),
                saved.getMemo(), actor));
        return saved;
    }

    /**
     * Posts a reversing entry that offsets a manual POSTED entry — corrections are made by reversal, never
     * by editing. The reversal mirrors the original line-for-line with debit/credit swapped and posts
     * through {@link #post}, inheriting period-OPEN, balance, gapless numbering and the posted event. Both
     * entries stay POSTED, linked by {@code reverses}/{@code reversedBy}, so their lines net to zero in
     * every {@code status='POSTED'} balance query. Only manual entries (no subledger source) are reversible
     * here; a document-sourced entry must be reversed via its owning module so the subledger stays equal to
     * its GL control account.
     */
    @Transactional
    public JournalEntry reverse(Long entryNo, LocalDate reversalDate, String memo, String actor) {
        JournalEntry original = journalEntryRepository.findByEntryNo(entryNo)
                .orElseThrow(() -> new EntryNotFoundException(entryNo));
        if (original.getStatus() != JournalEntryStatus.POSTED) {
            throw new EntryNotReversibleException("entry #" + entryNo + " is " + original.getStatus()
                    + "; only a POSTED entry can be reversed");
        }
        if (!original.isManual()) {
            throw new EntryNotReversibleException("entry #" + entryNo + " is document-sourced ("
                    + original.getSourceDocType() + "); reverse it through its owning document");
        }
        if (original.isReversed()) {
            throw new EntryNotReversibleException("entry #" + entryNo + " is already reversed by entry #"
                    + original.getReversedByEntryId());
        }

        LocalDate date = reversalDate != null ? reversalDate : LocalDate.now();
        String reversalMemo = memo != null && !memo.isBlank() ? memo : "Reversal of #" + entryNo;

        List<JournalEntryRequest.Line> reversedLines = new ArrayList<>();
        for (JournalLine line : original.getLines()) {
            Account account = accountRepository.findById(line.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(String.valueOf(line.getAccountId())));
            // Swap sides verbatim (no negation/netting) so debit-total == credit-total stays exact.
            reversedLines.add(new JournalEntryRequest.Line(account.getCode(), line.getCredit(),
                    line.getDebit(), line.getMemo(), line.getPartnerId()));
        }

        JournalEntryRequest request = new JournalEntryRequest(null, date, reversalMemo,
                original.getCurrencyCode(), null, null, null, reversedLines);
        JournalEntry reversal = post(request, actor);

        // Link both directions; both rows stay POSTED (the immutability trigger permits these columns).
        reversal.markReverses(original.getId());
        original.markReversedBy(reversal.getId());
        journalEntryRepository.saveAndFlush(reversal);
        journalEntryRepository.saveAndFlush(original);
        return reversal;
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
