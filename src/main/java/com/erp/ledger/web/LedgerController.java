package com.erp.ledger.web;

import com.erp.ledger.api.FiscalPeriodChangedEvent;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.application.FiscalPeriodService;
import com.erp.ledger.application.FiscalYearService;
import com.erp.ledger.application.JournalEntryDetail;
import com.erp.ledger.application.JournalEntryQueryService;
import com.erp.ledger.application.LedgerPostingService;
import com.erp.ledger.application.LedgerReportService;
import com.erp.ledger.application.TrialBalanceReport;
import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.JournalEntry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerPostingService postingService;
    private final LedgerReportService reportService;
    private final FiscalPeriodService fiscalPeriodService;
    private final FiscalYearService fiscalYearService;
    private final JournalEntryQueryService journalEntryQueryService;
    private final ApplicationEventPublisher events;

    public LedgerController(LedgerPostingService postingService, LedgerReportService reportService,
                           FiscalPeriodService fiscalPeriodService, FiscalYearService fiscalYearService,
                           JournalEntryQueryService journalEntryQueryService,
                           ApplicationEventPublisher events) {
        this.postingService = postingService;
        this.reportService = reportService;
        this.fiscalPeriodService = fiscalPeriodService;
        this.fiscalYearService = fiscalYearService;
        this.journalEntryQueryService = journalEntryQueryService;
        this.events = events;
    }

    /** Optional body for a reversal: a posting date (must land in an OPEN period) + memo, both defaultable. */
    public record ReverseEntryRequest(LocalDate reversalDate, String memo) {}

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }

    /** API view of a fiscal period's soft-close status. */
    public record FiscalPeriodResponse(String yearCode, int periodNo, LocalDate startDate,
                                       LocalDate endDate, String status) {
        static FiscalPeriodResponse from(String yearCode, FiscalPeriod period) {
            return new FiscalPeriodResponse(yearCode, period.getPeriodNo(), period.getStartDate(),
                    period.getEndDate(), period.getStatus().name());
        }
    }

    /** Posts a manual journal entry. */
    @PostMapping("/journal-entries")
    public ResponseEntity<JournalEntryResponse> post(@RequestBody JournalEntryRequest request,
                                                     Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        JournalEntry entry = postingService.post(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(JournalEntryResponse.from(entry));
    }

    /** Full detail of one entry (header + lines + reversal links), by its gapless business number. */
    @GetMapping("/journal-entries/{entryNo}")
    public JournalEntryDetail journalEntry(@PathVariable Long entryNo) {
        return journalEntryQueryService.findDetail(entryNo);
    }

    /**
     * Reverses a manual POSTED entry by posting a mirror entry that offsets it (debit/credit swapped). The
     * reversal lands in the given date's OPEN period (default today). Document-sourced and already-reversed
     * entries are refused (422); an unknown entry is 404.
     */
    @PostMapping("/journal-entries/{entryNo}/reverse")
    public ResponseEntity<JournalEntryResponse> reverse(@PathVariable Long entryNo,
                                                        @RequestBody(required = false) ReverseEntryRequest request,
                                                        Principal principal) {
        LocalDate date = request != null ? request.reversalDate() : null;
        String memo = request != null ? request.memo() : null;
        JournalEntry reversal = postingService.reverse(entryNo, date, memo, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(JournalEntryResponse.from(reversal));
    }

    /** The trial balance — proof that the posted ledger nets to zero. */
    @GetMapping("/trial-balance")
    public TrialBalanceReport trialBalance() {
        return reportService.trialBalance();
    }

    /** Soft-closes a fiscal period — blocks new postings into it. */
    @PostMapping("/fiscal-years/{yearCode}/periods/{periodNo}/close")
    public FiscalPeriodResponse closePeriod(@PathVariable String yearCode, @PathVariable int periodNo,
                                            Principal principal) {
        FiscalPeriod period = fiscalPeriodService.close(yearCode, periodNo);
        events.publishEvent(new FiscalPeriodChangedEvent(yearCode, periodNo,
                period.getStatus().name(), actor(principal)));
        return FiscalPeriodResponse.from(yearCode, period);
    }

    /** Reopens a soft-closed fiscal period. */
    @PostMapping("/fiscal-years/{yearCode}/periods/{periodNo}/reopen")
    public FiscalPeriodResponse reopenPeriod(@PathVariable String yearCode, @PathVariable int periodNo,
                                             Principal principal) {
        FiscalPeriod period = fiscalPeriodService.reopen(yearCode, periodNo);
        events.publishEvent(new FiscalPeriodChangedEvent(yearCode, periodNo,
                period.getStatus().name(), actor(principal)));
        return FiscalPeriodResponse.from(yearCode, period);
    }

    /**
     * Year-end hard close: posts the closing entry (revenue/expense → retained earnings 3200) and locks
     * every period of the year. Irreversible — locked periods cannot be reopened.
     */
    @PostMapping("/fiscal-years/{yearCode}/close-year")
    public FiscalYearService.YearEndCloseResult closeYear(@PathVariable String yearCode,
                                                          Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        return fiscalYearService.closeYear(yearCode, actor);
    }
}
