package com.erp.ledger.application;

/** Raised when a journal line references an account code that does not exist or is not postable. */
public class AccountNotFoundException extends LedgerException {

    public AccountNotFoundException(String accountCode) {
        super("no postable account with code " + accountCode);
    }
}
