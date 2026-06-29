package com.erp.ledger.api;

/** Published when a fiscal period is soft-closed or reopened (a sensitive, non-posting control change). */
public record FiscalPeriodChangedEvent(String yearCode, int periodNo, String newStatus, String actor) {}
