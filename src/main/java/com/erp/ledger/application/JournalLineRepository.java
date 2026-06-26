package com.erp.ledger.application;

import com.erp.ledger.domain.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /** Trial balance: posted debit/credit totals per account. The sums must net to zero overall. */
    @Query(value = """
            SELECT a.code          AS code,
                   a.name          AS name,
                   a.account_class AS accountClass,
                   COALESCE(SUM(l.debit), 0)  AS totalDebit,
                   COALESCE(SUM(l.credit), 0) AS totalCredit
            FROM journal_line l
            JOIN journal_entry e ON e.id = l.journal_entry_id
            JOIN account a       ON a.id = l.account_id
            WHERE e.status = 'POSTED'
            GROUP BY a.code, a.name, a.account_class
            ORDER BY a.code
            """, nativeQuery = true)
    List<TrialBalanceRow> trialBalance();
}
