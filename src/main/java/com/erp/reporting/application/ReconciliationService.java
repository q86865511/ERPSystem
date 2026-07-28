package com.erp.reporting.application;

import com.erp.inventory.api.InventoryAccountBalance;
import com.erp.inventory.api.InventoryQuery;
import com.erp.ledger.api.AccountBalance;
import com.erp.ledger.api.GeneralLedgerQuery;
import com.erp.purchasing.api.PayablesQuery;
import com.erp.reporting.application.ReconciliationReport.ClearingBalance;
import com.erp.reporting.application.ReconciliationReport.SubledgerCheck;
import com.erp.sales.api.ReceivablesQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the reconciliation health-check by composing the ledger's posted balances with each module's
 * published subledger balance — the trial balance must balance, the inventory/AP/AR subledgers must equal
 * their GL control accounts, and the transitional clearing accounts are reported for visibility.
 */
@Service
public class ReconciliationService {

    private static final String AP_ACCOUNT = "2100";
    private static final String AR_ACCOUNT = "1200";
    private static final List<String> CLEARING_ACCOUNTS = List.of("2150", "1340", "1320");

    private final GeneralLedgerQuery generalLedgerQuery;
    private final InventoryQuery inventoryQuery;
    private final PayablesQuery payablesQuery;
    private final ReceivablesQuery receivablesQuery;

    public ReconciliationService(GeneralLedgerQuery generalLedgerQuery,
                                 InventoryQuery inventoryQuery,
                                 PayablesQuery payablesQuery,
                                 ReceivablesQuery receivablesQuery) {
        this.generalLedgerQuery = generalLedgerQuery;
        this.inventoryQuery = inventoryQuery;
        this.payablesQuery = payablesQuery;
        this.receivablesQuery = receivablesQuery;
    }

    /**
     * One read transaction for the whole report: the GL balances and the subledger reads must come from a
     * single snapshot, otherwise a posting that lands between them tears the comparison and the hero
     * reports a false red (or a false green). REPEATABLE READ because under READ COMMITTED every
     * statement would take its own snapshot even inside one transaction; a read-only snapshot never
     * conflicts, so it costs nothing but the snapshot age.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport reconcile(LocalDate asOf) {
        List<AccountBalance> balances = generalLedgerQuery.accountBalances(asOf);
        Map<String, AccountBalance> byCode = new LinkedHashMap<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (AccountBalance b : balances) {
            byCode.put(b.code(), b);
            totalDebit = totalDebit.add(b.totalDebit());
            totalCredit = totalCredit.add(b.totalCredit());
        }
        boolean trialBalanceBalanced = totalDebit.compareTo(totalCredit) == 0;

        List<SubledgerCheck> checks = new ArrayList<>();
        for (InventoryAccountBalance inv : inventoryQuery.subledgerByAccount()) {
            checks.add(subledgerCheck("Inventory", inv.accountCode(), inv.value(), byCode));
        }
        checks.add(subledgerCheck("Accounts Payable", AP_ACCOUNT,
                payablesQuery.apSubledgerBalance(), byCode));
        checks.add(subledgerCheck("Accounts Receivable", AR_ACCOUNT,
                receivablesQuery.arSubledgerBalance(), byCode));

        List<ClearingBalance> clearing = new ArrayList<>();
        for (String code : CLEARING_ACCOUNTS) {
            BigDecimal balance = gl(byCode, code);
            String name = byCode.containsKey(code) ? byCode.get(code).name() : code;
            clearing.add(new ClearingBalance(code, name, balance, balance.signum() == 0));
        }

        boolean healthy = trialBalanceBalanced && checks.stream().allMatch(SubledgerCheck::reconciled);
        return new ReconciliationReport(asOf, healthy, trialBalanceBalanced, checks, clearing);
    }

    private static SubledgerCheck subledgerCheck(String label, String accountCode, BigDecimal subledger,
                                                 Map<String, AccountBalance> byCode) {
        BigDecimal glControl = gl(byCode, accountCode);
        return new SubledgerCheck(label, accountCode, subledger, glControl,
                subledger.compareTo(glControl) == 0);
    }

    /** GL control balance in the account's natural (normal-balance) sign; zero if it has no postings. */
    private static BigDecimal gl(Map<String, AccountBalance> byCode, String accountCode) {
        AccountBalance balance = byCode.get(accountCode);
        return balance != null ? balance.naturalBalance() : BigDecimal.ZERO;
    }
}
