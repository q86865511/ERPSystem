package com.erp.ledger.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Read-side queries over the ledger. */
@Service
public class LedgerReportService {

    private final JournalLineRepository journalLineRepository;

    public LedgerReportService(JournalLineRepository journalLineRepository) {
        this.journalLineRepository = journalLineRepository;
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport trialBalance() {
        List<TrialBalanceLine> lines = journalLineRepository.trialBalance().stream()
                .map(r -> new TrialBalanceLine(
                        r.getCode(), r.getName(), r.getAccountClass(),
                        nullToZero(r.getTotalDebit()), nullToZero(r.getTotalCredit())))
                .toList();

        BigDecimal totalDebit = lines.stream()
                .map(TrialBalanceLine::totalDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .map(TrialBalanceLine::totalCredit).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TrialBalanceReport(lines, totalDebit, totalCredit,
                totalDebit.compareTo(totalCredit) == 0);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
