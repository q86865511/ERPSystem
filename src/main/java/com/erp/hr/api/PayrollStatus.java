package com.erp.hr.api;

/** Lifecycle of a payroll run: calculated as DRAFT, then POSTED to the general ledger. */
public enum PayrollStatus {
    DRAFT,
    POSTED
}
