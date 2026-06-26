package com.erp.inventory.application;

import java.math.BigDecimal;

/**
 * The inventory subledger value rolled up to one ledger control account (e.g. 1310 Raw Materials) —
 * the sum of the moving-average value of every item that maps to that account. The reconciliation
 * health-check asserts this equals the account's General Ledger balance.
 */
public record AccountSubledgerValue(String accountCode, BigDecimal subledgerValue) {
}
