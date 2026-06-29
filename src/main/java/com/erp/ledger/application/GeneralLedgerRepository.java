package com.erp.ledger.application;

import com.erp.ledger.domain.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Read-side queries for general-ledger reporting (posted balances and lines, as of a date). */
public interface GeneralLedgerRepository extends JpaRepository<JournalLine, Long> {

    @Query(value = """
            SELECT a.code           AS code,
                   a.name           AS name,
                   a.account_class  AS accountClass,
                   a.normal_balance AS normalBalance,
                   COALESCE(SUM(l.debit), 0)  AS totalDebit,
                   COALESCE(SUM(l.credit), 0) AS totalCredit
            FROM journal_line l
            JOIN journal_entry e ON e.id = l.journal_entry_id
            JOIN account a       ON a.id = l.account_id
            WHERE e.status = 'POSTED' AND e.posting_date <= :asOf
            GROUP BY a.code, a.name, a.account_class, a.normal_balance
            ORDER BY a.code
            """, nativeQuery = true)
    List<AccountBalanceRow> accountBalancesAsOf(@Param("asOf") LocalDate asOf);

    /** Posted balances per account within a date range (inclusive) — used to scope a year-end close to a
        single fiscal year's revenue/expense activity, rather than a cumulative as-of total. */
    @Query(value = """
            SELECT a.code           AS code,
                   a.name           AS name,
                   a.account_class  AS accountClass,
                   a.normal_balance AS normalBalance,
                   COALESCE(SUM(l.debit), 0)  AS totalDebit,
                   COALESCE(SUM(l.credit), 0) AS totalCredit
            FROM journal_line l
            JOIN journal_entry e ON e.id = l.journal_entry_id
            JOIN account a       ON a.id = l.account_id
            WHERE e.status = 'POSTED' AND e.posting_date BETWEEN :from AND :to
            GROUP BY a.code, a.name, a.account_class, a.normal_balance
            ORDER BY a.code
            """, nativeQuery = true)
    List<AccountBalanceRow> accountBalancesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT e.id              AS entryId,
                   e.entry_no        AS entryNo,
                   e.posting_date    AS postingDate,
                   a.code            AS accountCode,
                   l.debit           AS debit,
                   l.credit          AS credit,
                   l.memo            AS memo,
                   e.source_doc_type AS sourceDocType,
                   e.source_doc_id   AS sourceDocId
            FROM journal_line l
            JOIN journal_entry e ON e.id = l.journal_entry_id
            JOIN account a       ON a.id = l.account_id
            WHERE e.status = 'POSTED' AND a.code = :code AND e.posting_date <= :asOf
            ORDER BY e.posting_date, e.entry_no, l.line_no
            """, nativeQuery = true)
    List<LedgerLineRow> linesForAccountAsOf(@Param("code") String code, @Param("asOf") LocalDate asOf);
}
