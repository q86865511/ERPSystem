package com.erp.reporting.application;

import com.erp.ledger.api.AccountBalance;
import com.erp.ledger.api.GeneralLedgerQuery;
import com.erp.ledger.api.LedgerLineView;
import com.erp.reporting.domain.Budget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Read-side finance analytics that back the finance-centre charts: monthly revenue/gross-margin and cash
 * trends, headline KPIs with period-over-period deltas, and budget vs actual — all aggregated from the
 * ledger's published posted lines (plus the budget master for the variance report).
 */
@Service
@Transactional(readOnly = true)
public class FinanceAnalyticsService {

    private static final String REVENUE = "4100";
    private static final String COGS = "5100";
    private static final String CASH = "1010";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GeneralLedgerQuery generalLedgerQuery;
    private final BudgetRepository budgetRepository;

    public FinanceAnalyticsService(GeneralLedgerQuery generalLedgerQuery,
                                   BudgetRepository budgetRepository) {
        this.generalLedgerQuery = generalLedgerQuery;
        this.budgetRepository = budgetRepository;
    }

    public List<RevenuePoint> revenueTrend(int months, LocalDate asOf) {
        List<LedgerLineView> rev = generalLedgerQuery.linesForAccount(REVENUE, asOf);
        List<LedgerLineView> cogs = generalLedgerQuery.linesForAccount(COGS, asOf);
        List<RevenuePoint> out = new ArrayList<>();
        for (YearMonth ym : lastMonths(YearMonth.from(asOf), months)) {
            BigDecimal revenue = creditNet(rev, ym);
            BigDecimal cogsAmount = debitNet(cogs, ym);
            out.add(new RevenuePoint(ym.toString(), revenue, cogsAmount, revenue.subtract(cogsAmount)));
        }
        return out;
    }

    public List<CashFlowPoint> cashFlow(int months, LocalDate asOf) {
        List<LedgerLineView> cash = generalLedgerQuery.linesForAccount(CASH, asOf);
        List<CashFlowPoint> out = new ArrayList<>();
        for (YearMonth ym : lastMonths(YearMonth.from(asOf), months)) {
            BigDecimal inflow = sum(cash, ym, LedgerLineView::debit);
            BigDecimal outflow = sum(cash, ym, LedgerLineView::credit);
            out.add(new CashFlowPoint(ym.toString(), inflow, outflow, inflow.subtract(outflow)));
        }
        return out;
    }

    public KpiSummary kpiSummary(LocalDate asOf) {
        YearMonth current = YearMonth.from(asOf);
        YearMonth previous = current.minusMonths(1);
        // Compare like-for-like: the current month runs from its 1st through asOf (month-to-date), so the
        // previous period must be the same slice of last month, not the whole month — otherwise a fresh
        // month always reads as a steep drop. The previous window is last month's 1st through the same
        // day-of-month, clamped to that month's length (e.g. asOf on the 31st still lands on Feb's last day).
        Period cur = new Period(current.atDay(1), asOf);
        Period prev = new Period(previous.atDay(1), clampDayOfMonth(previous, asOf.getDayOfMonth()));
        List<LedgerLineView> rev = generalLedgerQuery.linesForAccount(REVENUE, asOf);
        List<LedgerLineView> cogs = generalLedgerQuery.linesForAccount(COGS, asOf);
        List<LedgerLineView> cash = generalLedgerQuery.linesForAccount(CASH, asOf);

        KpiSummary.Metric revenue = metric(creditNet(rev, cur), creditNet(rev, prev));
        KpiSummary.Metric grossProfit = metric(
                creditNet(rev, cur).subtract(debitNet(cogs, cur)),
                creditNet(rev, prev).subtract(debitNet(cogs, prev)));
        KpiSummary.Metric netCash = metric(
                sum(cash, cur, LedgerLineView::debit).subtract(sum(cash, cur, LedgerLineView::credit)),
                sum(cash, prev, LedgerLineView::debit).subtract(sum(cash, prev, LedgerLineView::credit)));
        return new KpiSummary(current.toString(), previous.toString(), revenue, grossProfit, netCash);
    }

    public BudgetVarianceReport budgetVariance(int year, int month, LocalDate asOf) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthEnd = ym.atEndOfMonth();
        Map<String, AccountBalance> byCode = balancesByCode(monthEnd);
        List<BudgetVarianceReport.Line> lines = new ArrayList<>();
        for (Budget budget : budgetRepository.findByPeriodYearOrderByAccountCode(year)) {
            AccountBalance account = byCode.get(budget.getAccountCode());
            String name = account != null ? account.name() : budget.getAccountCode();
            boolean debitNormal = account == null || "DEBIT".equals(account.normalBalance());
            List<LedgerLineView> accountLines =
                    generalLedgerQuery.linesForAccount(budget.getAccountCode(), monthEnd);
            BigDecimal actual = debitNormal ? debitNet(accountLines, ym) : creditNet(accountLines, ym);
            BigDecimal planned = budget.getMonthlyBudget();
            lines.add(new BudgetVarianceReport.Line(budget.getAccountCode(), name, planned, actual,
                    actual.subtract(planned)));
        }
        return new BudgetVarianceReport(year, month, lines);
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private Map<String, AccountBalance> balancesByCode(LocalDate asOf) {
        Map<String, AccountBalance> byCode = new LinkedHashMap<>();
        for (AccountBalance balance : generalLedgerQuery.accountBalances(asOf)) {
            byCode.put(balance.code(), balance);
        }
        return byCode;
    }

    private static List<YearMonth> lastMonths(YearMonth end, int months) {
        int count = Math.min(Math.max(months, 1), 36);
        List<YearMonth> result = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            result.add(end.minusMonths(i));
        }
        return result;
    }

    private static BigDecimal sum(List<LedgerLineView> lines, YearMonth ym,
                                  Function<LedgerLineView, BigDecimal> field) {
        return lines.stream()
                .filter(l -> YearMonth.from(l.postingDate()).equals(ym))
                .map(l -> nz(field.apply(l)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal creditNet(List<LedgerLineView> lines, YearMonth ym) {
        return sum(lines, ym, LedgerLineView::credit).subtract(sum(lines, ym, LedgerLineView::debit));
    }

    private static BigDecimal debitNet(List<LedgerLineView> lines, YearMonth ym) {
        return sum(lines, ym, LedgerLineView::debit).subtract(sum(lines, ym, LedgerLineView::credit));
    }

    /** An inclusive posting-date window [from, to] used for month-to-date KPI aggregation. */
    private record Period(LocalDate from, LocalDate to) {
        boolean contains(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }

    /** {@code day} in {@code ym}, clamped to the month's length so e.g. day 31 in February lands on the 28th/29th. */
    private static LocalDate clampDayOfMonth(YearMonth ym, int day) {
        return ym.atDay(Math.min(day, ym.lengthOfMonth()));
    }

    private static BigDecimal sum(List<LedgerLineView> lines, Period period,
                                  Function<LedgerLineView, BigDecimal> field) {
        return lines.stream()
                .filter(l -> period.contains(l.postingDate()))
                .map(l -> nz(field.apply(l)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal creditNet(List<LedgerLineView> lines, Period period) {
        return sum(lines, period, LedgerLineView::credit).subtract(sum(lines, period, LedgerLineView::debit));
    }

    private static BigDecimal debitNet(List<LedgerLineView> lines, Period period) {
        return sum(lines, period, LedgerLineView::debit).subtract(sum(lines, period, LedgerLineView::credit));
    }

    private static KpiSummary.Metric metric(BigDecimal current, BigDecimal previous) {
        BigDecimal deltaPct = previous.signum() == 0
                ? BigDecimal.ZERO
                : current.subtract(previous).multiply(HUNDRED).divide(previous.abs(), 1, RoundingMode.HALF_UP);
        return new KpiSummary.Metric(current, previous, deltaPct);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
