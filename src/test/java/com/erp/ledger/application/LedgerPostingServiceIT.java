package com.erp.ledger.application;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.JournalEntry;
import com.erp.ledger.domain.JournalEntryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the heart of the system: posting through {@link LedgerPostingService} against
 * a real PostgreSQL (Testcontainers). These assert the accounting invariants directly — balance,
 * period gating, idempotency and DB-enforced immutability.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LedgerPostingServiceIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);

    @Autowired
    private LedgerPostingService postingService;
    @Autowired
    private LedgerReportService reportService;
    @Autowired
    private FiscalPeriodRepository fiscalPeriodRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static JournalEntryRequest cashSale(String docId, String debit, String credit, LocalDate date) {
        boolean tracked = docId != null;
        return new JournalEntryRequest(
                null, date, "test entry", "TWD",
                tracked ? "TEST" : null, docId, tracked ? "POST" : null,
                List.of(
                        new JournalEntryRequest.Line("1010", new BigDecimal(debit), null, "cash"),
                        new JournalEntryRequest.Line("4100", null, new BigDecimal(credit), "revenue")));
    }

    @Test
    void postsBalancedEntryAndTrialBalanceBalances() {
        JournalEntry entry = postingService.post(cashSale(null, "1000.00", "1000.00", JUNE), "tester");

        assertThat(entry.getStatus()).isEqualTo(JournalEntryStatus.POSTED);
        assertThat(entry.getEntryNo()).isNotNull();

        TrialBalanceReport tb = reportService.trialBalance();
        assertThat(tb.balanced()).isTrue();
        assertThat(tb.totalDebit()).isEqualByComparingTo(tb.totalCredit());
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> postingService.post(cashSale(null, "100.00", "90.00", JUNE), "tester"))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void rejectsPostingWhenNoOpenPeriodExists() {
        assertThatThrownBy(() -> postingService.post(
                cashSale(null, "10.00", "10.00", LocalDate.of(2099, 1, 1)), "tester"))
                .isInstanceOf(PeriodNotOpenException.class);
    }

    @Test
    void rejectsPostingIntoClosedPeriod() {
        LocalDate february = LocalDate.of(2026, 2, 15);
        FiscalPeriod period = fiscalPeriodRepository.findByDate(february).orElseThrow();
        period.close();
        fiscalPeriodRepository.saveAndFlush(period);

        assertThatThrownBy(() -> postingService.post(cashSale(null, "10.00", "10.00", february), "tester"))
                .isInstanceOf(PeriodNotOpenException.class);
    }

    @Test
    void repostingSameSourceEventIsIdempotent() {
        JournalEntryRequest request = cashSale("IDEM-1", "250.00", "250.00", JUNE);

        JournalEntry first = postingService.post(request, "tester");
        JournalEntry second = postingService.post(request, "tester");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getEntryNo()).isEqualTo(first.getEntryNo());
    }

    @Test
    void postedLineCannotBeMutatedBecauseDbTriggerBlocksIt() {
        JournalEntry entry = postingService.post(cashSale("IMMUT-1", "77.00", "77.00", JUNE), "tester");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE journal_line SET debit = debit + 1 WHERE journal_entry_id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void appendingALineToAPostedEntryIsRejectedByTheDbBalanceGuard() {
        JournalEntry entry = postingService.post(cashSale("APPEND-1", "42.00", "42.00", JUNE), "tester");
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM account WHERE code = '1010'", Long.class);

        // journal_line INSERT stays open on a POSTED parent (the posting flow needs it), so the guard
        // has to be the balance re-check at COMMIT — V24.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO journal_line (journal_entry_id, line_no, account_id, debit, credit) "
                        + "VALUES (?, 99, ?, 100, 0)", entry.getId(), accountId))
                .isInstanceOf(DataAccessException.class);

        // The entry is untouched and still balanced.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(debit - credit), 0) FROM journal_line WHERE journal_entry_id = ?",
                BigDecimal.class, entry.getId())).isEqualByComparingTo("0");
    }

    @Test
    void aBalancedPairAppendedToAPostedEntryStillCommits() {
        JournalEntry entry = postingService.post(cashSale("APPEND-2", "42.00", "42.00", JUNE), "tester");
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM account WHERE code = '1010'", Long.class);

        // The guard only asserts balance — it must not block the posting flow's own line inserts.
        jdbcTemplate.update(
                "INSERT INTO journal_line (journal_entry_id, line_no, account_id, debit, credit) "
                        + "VALUES (?, 98, ?, 5, 0), (?, 99, ?, 0, 5)",
                entry.getId(), accountId, entry.getId(), accountId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(debit - credit), 0) FROM journal_line WHERE journal_entry_id = ?",
                BigDecimal.class, entry.getId())).isEqualByComparingTo("0");
    }
}
