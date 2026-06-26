package com.erp.ledger.web;

import com.erp.ledger.application.JournalEntryRequest;
import com.erp.ledger.application.LedgerPostingService;
import com.erp.ledger.application.LedgerReportService;
import com.erp.ledger.application.TrialBalanceReport;
import com.erp.ledger.domain.JournalEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerPostingService postingService;
    private final LedgerReportService reportService;

    public LedgerController(LedgerPostingService postingService, LedgerReportService reportService) {
        this.postingService = postingService;
        this.reportService = reportService;
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
}
