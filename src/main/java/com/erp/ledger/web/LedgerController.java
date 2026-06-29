package com.erp.ledger.web;

import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.application.FiscalPeriodService;
import com.erp.ledger.application.FiscalYearService;
import com.erp.ledger.application.LedgerPostingService;
import com.erp.ledger.application.LedgerReportService;
import com.erp.ledger.application.TrialBalanceReport;
import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.JournalEntry;
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

    public LedgerController(LedgerPostingService postingService, LedgerReportService reportService,
                           FiscalPeriodService fiscalPeriodService, FiscalYearService fiscalYearService) {
        this.postingService = postingService;
        this.reportService = reportService;
        this.fiscalPeriodService = fiscalPeriodService;
        this.fiscalYearService = fiscalYearService;
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

    /** The trial balance — proof that the posted ledger nets to zero. */
    @GetMapping("/trial-balance")
    public TrialBalanceReport trialBalance() {
        return reportService.trialBalance();
    }

    /** Soft-closes a fiscal period — blocks new postings into it. */
    @PostMapping("/fiscal-years/{yearCode}/periods/{periodNo}/close")
    public FiscalPeriodResponse closePeriod(@PathVariable String yearCode, @PathVariable int periodNo) {
        return FiscalPeriodResponse.from(yearCode, fiscalPeriodService.close(yearCode, periodNo));
    }

    /** Reopens a soft-closed fiscal period. */
    @PostMapping("/fiscal-years/{yearCode}/periods/{periodNo}/reopen")
    public FiscalPeriodResponse reopenPeriod(@PathVariable String yearCode, @PathVariable int periodNo) {
        return FiscalPeriodResponse.from(yearCode, fiscalPeriodService.reopen(yearCode, periodNo));
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
