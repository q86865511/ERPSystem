package com.erp.hr.api;

/** Lifecycle of a leave request: it is filed PENDING, then approved or rejected. */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED
}
