package com.erp.ledger.application;

import com.erp.ledger.api.AccountBalance;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.FiscalPeriodStatus;
import com.erp.ledger.domain.FiscalYear;
import com.erp.ledger.domain.JournalEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Year-end hard close. Posts a closing journal entry that zeroes the year's revenue and expense accounts
 * and carries the net result to Retained Earnings (3200), then locks every period of the year so nothing —
 * not even a reversal — can post into it again.
 *
 * <p>The closing entry's profit/loss is scoped to the fiscal year's own date range (not a cumulative
 * as-of total), so it closes only that year's activity and stays correct when multiple years coexist.
 * Because zeroing the revenue/expense accounts offsets their cumulative balances, the reporting module's
 * dynamically-computed "current-period earnings" naturally becomes zero afterwards while 3200 holds the
 * retained earnings — the balance sheet stays balanced with no reporting change.
 */
@Service
public class FiscalYearService {

    static final String RETAINED_EARNINGS_CODE = "3200";
    static final String SOURCE_DOC_TYPE = "YEAR_END_CLOSE";
    static final String SOURCE_EVENT = "CLOSE";
    private static final String REVENUE = "REVENUE";
    private static final String EXPENSE = "EXPENSE";
    private static final String MEMO = "Year-end close";

    private final FiscalYearRepository fiscalYearRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final GeneralLedgerQueryService generalLedgerQueryService;
    private final LedgerPostingService postingService;

    public FiscalYearService(FiscalYearRepository fiscalYearRepository,
                             FiscalPeriodRepository fiscalPeriodRepository,
                             GeneralLedgerQueryService generalLedgerQueryService,
                             LedgerPostingService postingService) {
        this.fiscalYearRepository = fiscalYearRepository;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
        this.generalLedgerQueryService = generalLedgerQueryService;
        this.postingService = postingService;
    }

    /** Result of a year-end close. {@code closingEntryNo} is null for a year with no profit/loss activity. */
    public record YearEndCloseResult(String yearCode, Long closingEntryNo, BigDecimal netIncome,
                                     int periodsLocked, String yearStatus) {}

    @Transactional
    public YearEndCloseResult closeYear(String yearCode, String actor) {
        FiscalYear year = fiscalYearRepository.findByCode(yearCode)
                .orElseThrow(() -> new InvalidLedgerRequestException("unknown fiscal year: " + yearCode));
        if (year.isClosed()) {
            throw new YearAlreadyClosedException(yearCode);
        }

        List<FiscalPeriod> periods = fiscalPeriodRepository.findByFiscalYearIdOrderByPeriodNo(year.getId());

        List<JournalEntryRequest.Line> lines = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (AccountBalance b : generalLedgerQueryService
                .accountBalancesBetween(year.getStartDate(), year.getEndDate())) {
            BigDecimal nb = b.naturalBalance();
            if (nb.signum() == 0) {
                continue;
            }
            if (REVENUE.equals(b.accountClass())) {
                totalRevenue = totalRevenue.add(nb);
                lines.add(closeLine(b.code(), nb, true));
            } else if (EXPENSE.equals(b.accountClass())) {
                totalExpense = totalExpense.add(nb);
                lines.add(closeLine(b.code(), nb, false));
            }
        }
        BigDecimal netIncome = totalRevenue.subtract(totalExpense);

        Long closingEntryNo = null;
        if (!lines.isEmpty()) {
            // Carry the net to retained earnings: profit credits 3200, loss debits it.
            if (netIncome.signum() > 0) {
                lines.add(new JournalEntryRequest.Line(RETAINED_EARNINGS_CODE, null, netIncome, MEMO));
            } else if (netIncome.signum() < 0) {
                lines.add(new JournalEntryRequest.Line(RETAINED_EARNINGS_CODE, netIncome.negate(), null, MEMO));
            }
            closingEntryNo = postClosingEntry(year, periods, yearCode, actor, lines);
        }

        periods.forEach(FiscalPeriod::lock);
        fiscalPeriodRepository.saveAll(periods);
        year.close();
        fiscalYearRepository.save(year);

        return new YearEndCloseResult(yearCode, closingEntryNo, netIncome, periods.size(),
                year.getStatus().name());

    }

    private Long postClosingEntry(FiscalYear year, List<FiscalPeriod> periods, String yearCode,
                                  String actor, List<JournalEntryRequest.Line> lines) {
        // The closing entry is dated the year end, which falls in the last period. Posting requires that
        // period OPEN, so if it was soft-closed reopen it within this transaction before posting.
        FiscalPeriod last = periods.get(periods.size() - 1);
        if (last.getStatus() == FiscalPeriodStatus.CLOSED) {
            last.reopen();
            fiscalPeriodRepository.saveAndFlush(last);
        }
        JournalEntryRequest request = new JournalEntryRequest(null, year.getEndDate(),
                MEMO + " " + yearCode, null, SOURCE_DOC_TYPE, yearCode, SOURCE_EVENT, lines);
        JournalEntry entry = postingService.post(request, actor);
        return entry.getEntryNo();
    }

    /**
     * Builds one closing leg that zeroes an account given its natural-sign balance {@code nb}. A revenue
     * (credit-normal) balance is zeroed by a debit; an expense (debit-normal) by a credit; an abnormal
     * balance flips to the opposite side. {@code nb} is never zero here (skipped by the caller).
     */
    private static JournalEntryRequest.Line closeLine(String code, BigDecimal nb, boolean revenue) {
        boolean debitSide = revenue ? nb.signum() > 0 : nb.signum() < 0;
        BigDecimal amount = nb.abs();
        return debitSide
                ? new JournalEntryRequest.Line(code, amount, null, MEMO)
                : new JournalEntryRequest.Line(code, null, amount, MEMO);
    }
}
