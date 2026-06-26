package com.erp.reporting.web;

import com.erp.ledger.api.LedgerLineView;
import com.erp.reporting.application.BalanceSheet;
import com.erp.reporting.application.IncomeStatement;
import com.erp.reporting.application.ReportingService;
import com.erp.reporting.application.TrialBalance;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** REST surface for read-side financial reports, each taken as of a date (defaults to today). */
@RestController
@RequestMapping("/api/reporting")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/trial-balance")
    public TrialBalance trialBalance(@RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportingService.trialBalance(asOfOrToday(asOf));
    }

    @GetMapping("/income-statement")
    public IncomeStatement incomeStatement(@RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportingService.incomeStatement(asOfOrToday(asOf));
    }

    @GetMapping("/balance-sheet")
    public BalanceSheet balanceSheet(@RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportingService.balanceSheet(asOfOrToday(asOf));
    }

    @GetMapping("/general-ledger/{accountCode}")
    public List<LedgerLineView> generalLedger(@PathVariable String accountCode,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                              LocalDate asOf) {
        return reportingService.generalLedger(accountCode, asOfOrToday(asOf));
    }

    private static LocalDate asOfOrToday(LocalDate asOf) {
        return asOf != null ? asOf : LocalDate.now();
    }
}
