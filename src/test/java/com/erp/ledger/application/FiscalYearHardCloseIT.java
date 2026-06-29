package com.erp.ledger.application;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.AccountBalance;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Year-end hard close: a closing entry zeroes the year's revenue/expense (scoped to the year's date range)
 * and carries the net to retained earnings (3200), then every period locks. Uses dedicated isolated years
 * (2097 empty / 2098 loss / 2099 profit), seeded by the @Sql script, so locking them can't disturb other
 * ITs that share the Testcontainers database (which post into 2026).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Sql(scripts = "/db/hardclose-fiscal-years.sql")
class FiscalYearHardCloseIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private FiscalYearService fiscalYearService;
    @Autowired
    private FiscalPeriodService fiscalPeriodService;
    @Autowired
    private FiscalYearRepository fiscalYearRepository;
    @Autowired
    private GeneralLedgerQueryService generalLedgerQueryService;
    @Autowired
    private LedgerPosting ledgerPosting;

    @Test
    void profitYearClosesZeroesPlAndLocksPeriods() {
        postRevenue(LocalDate.of(2099, 6, 30), "1000");
        postExpense(LocalDate.of(2099, 6, 30), "300");

        FiscalYearService.YearEndCloseResult result = fiscalYearService.closeYear("2099", "tester");

        assertThat(result.netIncome()).isEqualByComparingTo("700");
        assertThat(result.closingEntryNo()).isNotNull();
        assertThat(result.periodsLocked()).isEqualTo(12);
        assertThat(result.yearStatus()).isEqualTo("CLOSED");

        // The year's revenue and expense net to zero (closing entry offset them); profit landed in 3200.
        assertThat(rangeClassTotal("2099", "REVENUE")).isEqualByComparingTo("0");
        assertThat(rangeClassTotal("2099", "EXPENSE")).isEqualByComparingTo("0");
        assertThat(rangeBalanceOf("2099", "3200")).isEqualByComparingTo("700");

        // Every period is locked.
        assertThat(fiscalPeriodService.getPeriod("2099", 1).isLocked()).isTrue();
        assertThat(fiscalPeriodService.getPeriod("2099", 12).isLocked()).isTrue();

        // Nothing can post into the locked year, locked periods can't be reopened, and re-closing is rejected.
        assertThatThrownBy(() -> postRevenue(LocalDate.of(2099, 7, 15), "50"))
                .isInstanceOf(PeriodNotOpenException.class);
        assertThatThrownBy(() -> fiscalPeriodService.reopen("2099", 6))
                .isInstanceOf(PeriodLockedException.class);
        assertThatThrownBy(() -> fiscalYearService.closeYear("2099", "tester"))
                .isInstanceOf(YearAlreadyClosedException.class);
    }

    @Test
    void lossYearDebitsRetainedEarnings() {
        postRevenue(LocalDate.of(2098, 6, 30), "200");
        postExpense(LocalDate.of(2098, 6, 30), "500");

        FiscalYearService.YearEndCloseResult result = fiscalYearService.closeYear("2098", "tester");

        assertThat(result.netIncome()).isEqualByComparingTo("-300");
        assertThat(result.closingEntryNo()).isNotNull();
        assertThat(rangeClassTotal("2098", "REVENUE")).isEqualByComparingTo("0");
        assertThat(rangeClassTotal("2098", "EXPENSE")).isEqualByComparingTo("0");
        // Loss reduces retained earnings (3200 is credit-normal, so a debit shows as a negative natural balance).
        assertThat(rangeBalanceOf("2098", "3200")).isEqualByComparingTo("-300");
    }

    @Test
    void emptyYearLocksWithoutAClosingEntry() {
        // A year with no profit/loss activity posts no closing entry (would be a zero/<2-line entry),
        // but still locks every period and marks the year closed — no exception.
        FiscalYearService.YearEndCloseResult result = fiscalYearService.closeYear("2097", "tester");

        assertThat(result.closingEntryNo()).isNull();
        assertThat(result.netIncome()).isEqualByComparingTo("0");
        assertThat(result.periodsLocked()).isEqualTo(12);
        assertThat(result.yearStatus()).isEqualTo("CLOSED");
        assertThat(fiscalPeriodService.getPeriod("2097", 6).isLocked()).isTrue();
    }

    private void postRevenue(LocalDate date, String amount) {
        post(date, "HC-REV-" + SEQ.incrementAndGet(),
                new Line("1010", new BigDecimal(amount), null, "cash"),
                new Line("4100", null, new BigDecimal(amount), "revenue"));
    }

    private void postExpense(LocalDate date, String amount) {
        post(date, "HC-EXP-" + SEQ.incrementAndGet(),
                new Line("6000", new BigDecimal(amount), null, "expense"),
                new Line("1010", null, new BigDecimal(amount), "cash"));
    }

    private void post(LocalDate date, String docId, Line... lines) {
        ledgerPosting.post(new JournalEntryRequest(null, date, "hard-close test " + docId, null,
                "HARDCLOSE_TEST", docId, "POSTED", List.of(lines)), "tester");
    }

    private BigDecimal rangeClassTotal(String yearCode, String accountClass) {
        return rangeBalances(yearCode).stream()
                .filter(b -> accountClass.equals(b.accountClass()))
                .map(AccountBalance::naturalBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal rangeBalanceOf(String yearCode, String accountCode) {
        return rangeBalances(yearCode).stream()
                .filter(b -> accountCode.equals(b.code()))
                .map(AccountBalance::naturalBalance)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private List<AccountBalance> rangeBalances(String yearCode) {
        var year = fiscalYearRepository.findByCode(yearCode).orElseThrow();
        return generalLedgerQueryService.accountBalancesBetween(year.getStartDate(), year.getEndDate());
    }
}
