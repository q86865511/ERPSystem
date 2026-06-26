package com.erp.reporting.application;

import com.erp.ledger.api.AccountBalance;
import com.erp.ledger.api.GeneralLedgerQuery;
import com.erp.ledger.api.LedgerLineView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-side financial statements built from the ledger's published balances: a trial balance, an income
 * statement, and a balance sheet whose equity carries current-period earnings (retained earnings are
 * computed dynamically, with no year-end close). All are taken as of a date.
 */
@Service
public class ReportingService {

    private final GeneralLedgerQuery generalLedgerQuery;

    public ReportingService(GeneralLedgerQuery generalLedgerQuery) {
        this.generalLedgerQuery = generalLedgerQuery;
    }

    public TrialBalance trialBalance(LocalDate asOf) {
        List<AccountBalance> balances = generalLedgerQuery.accountBalances(asOf);
        List<TrialBalance.Line> lines = balances.stream()
                .map(b -> new TrialBalance.Line(b.code(), b.name(), b.accountClass(),
                        b.totalDebit(), b.totalCredit()))
                .toList();
        BigDecimal totalDebit = balances.stream()
                .map(AccountBalance::totalDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = balances.stream()
                .map(AccountBalance::totalCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TrialBalance(asOf, lines, totalDebit, totalCredit,
                totalDebit.compareTo(totalCredit) == 0);
    }

    public IncomeStatement incomeStatement(LocalDate asOf) {
        List<AccountBalance> balances = generalLedgerQuery.accountBalances(asOf);
        List<StatementLine> revenue = statementLines(balances, "REVENUE");
        List<StatementLine> expenses = statementLines(balances, "EXPENSE");
        BigDecimal totalRevenue = total(revenue);
        BigDecimal totalExpenses = total(expenses);
        return new IncomeStatement(asOf, revenue, expenses, totalRevenue, totalExpenses,
                totalRevenue.subtract(totalExpenses));
    }

    public BalanceSheet balanceSheet(LocalDate asOf) {
        List<AccountBalance> balances = generalLedgerQuery.accountBalances(asOf);
        List<StatementLine> assets = statementLines(balances, "ASSET");
        List<StatementLine> liabilities = statementLines(balances, "LIABILITY");

        BigDecimal netIncome = sumNatural(balances, "REVENUE").subtract(sumNatural(balances, "EXPENSE"));

        // Equity = posted equity accounts + current-period earnings (retained earnings, dynamic).
        List<StatementLine> equity = new java.util.ArrayList<>(statementLines(balances, "EQUITY"));
        equity.add(new StatementLine("RE", "Current-period earnings", netIncome));

        BigDecimal totalAssets = sumNatural(balances, "ASSET");
        BigDecimal totalLiabilities = sumNatural(balances, "LIABILITY");
        BigDecimal totalEquity = total(equity);
        boolean balanced = totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
        return new BalanceSheet(asOf, assets, liabilities, equity, totalAssets, totalLiabilities,
                totalEquity, netIncome, balanced);
    }

    public List<LedgerLineView> generalLedger(String accountCode, LocalDate asOf) {
        return generalLedgerQuery.linesForAccount(accountCode, asOf);
    }

    private static List<StatementLine> statementLines(List<AccountBalance> balances, String accountClass) {
        return balances.stream()
                .filter(b -> accountClass.equals(b.accountClass()))
                .map(b -> new StatementLine(b.code(), b.name(), b.naturalBalance()))
                .toList();
    }

    private static BigDecimal sumNatural(List<AccountBalance> balances, String accountClass) {
        return balances.stream()
                .filter(b -> accountClass.equals(b.accountClass()))
                .map(AccountBalance::naturalBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(List<StatementLine> lines) {
        return lines.stream().map(StatementLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
