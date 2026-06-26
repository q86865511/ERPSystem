package com.erp.purchasing.domain;

/** Result of matching a bill line against its receipts (three-way match, reporting only — never gates). */
public enum BillMatchStatus {
    MATCHED,
    PRICE_VARIANCE
}
