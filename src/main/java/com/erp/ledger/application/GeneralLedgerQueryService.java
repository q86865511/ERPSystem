package com.erp.ledger.application;

import com.erp.ledger.api.AccountBalance;
import com.erp.ledger.api.GeneralLedgerQuery;
import com.erp.ledger.api.LedgerLineView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Read-side implementation of the {@link GeneralLedgerQuery} port over posted journal lines. */
@Service
@Transactional(readOnly = true)
public class GeneralLedgerQueryService implements GeneralLedgerQuery {

    private final GeneralLedgerRepository generalLedgerRepository;

    public GeneralLedgerQueryService(GeneralLedgerRepository generalLedgerRepository) {
        this.generalLedgerRepository = generalLedgerRepository;
    }

    @Override
    public List<AccountBalance> accountBalances(LocalDate asOf) {
        return generalLedgerRepository.accountBalancesAsOf(asOf).stream()
                .map(r -> new AccountBalance(r.getCode(), r.getName(), r.getAccountClass(),
                        r.getNormalBalance(), r.getTotalDebit(), r.getTotalCredit()))
                .toList();
    }

    /** Posted balances within a date range (inclusive). Internal to the ledger module (not on the port);
        used by the year-end close to scope revenue/expense to a single fiscal year. */
    public List<AccountBalance> accountBalancesBetween(LocalDate from, LocalDate to) {
        return generalLedgerRepository.accountBalancesBetween(from, to).stream()
                .map(r -> new AccountBalance(r.getCode(), r.getName(), r.getAccountClass(),
                        r.getNormalBalance(), r.getTotalDebit(), r.getTotalCredit()))
                .toList();
    }

    @Override
    public List<LedgerLineView> linesForAccount(String accountCode, LocalDate asOf) {
        return generalLedgerRepository.linesForAccountAsOf(accountCode, asOf).stream()
                .map(r -> new LedgerLineView(r.getEntryId(), r.getEntryNo(), r.getPostingDate(),
                        r.getAccountCode(), r.getDebit(), r.getCredit(), r.getMemo(),
                        r.getSourceDocType(), r.getSourceDocId()))
                .toList();
    }
}
