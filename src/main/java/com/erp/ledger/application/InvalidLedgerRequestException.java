package com.erp.ledger.application;

/** Raised when a posting request is structurally invalid (no lines, both sides set, etc.). */
public class InvalidLedgerRequestException extends LedgerException {

    public InvalidLedgerRequestException(String message) {
        super(message);
    }
}
