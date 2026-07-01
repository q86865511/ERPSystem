package com.erp.hr.application;

/**
 * An HR operation conflicts with the current state of a resource — a duplicate record (attendance already
 * logged for a day, timesheet already logged for a week) or an illegal state transition (approving a
 * non-pending leave request, submitting a non-draft timesheet). Mapped to HTTP 409 by the web layer.
 */
public class HrConflictException extends HrException {

    public HrConflictException(String message) {
        super(message);
    }
}
