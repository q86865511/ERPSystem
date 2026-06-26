package com.erp.sales.api;

import java.math.BigDecimal;

/** The sales module's published read port for the accounts-receivable subledger balance. */
public interface ReceivablesQuery {

    /** Sum of every open customer invoice's balance — reconciles to the GL Accounts Receivable (1200). */
    BigDecimal arSubledgerBalance();
}
