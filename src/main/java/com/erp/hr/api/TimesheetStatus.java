package com.erp.hr.api;

/** Lifecycle of a weekly timesheet: DRAFT while being logged, SUBMITTED for review, then APPROVED. */
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED
}
