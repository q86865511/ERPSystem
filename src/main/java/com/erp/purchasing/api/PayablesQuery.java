package com.erp.purchasing.api;

import java.math.BigDecimal;

/** The purchasing module's published read port for the accounts-payable subledger balance. */
public interface PayablesQuery {

    /** Sum of every open vendor bill's balance — reconciles to the GL Accounts Payable (2100) control. */
    BigDecimal apSubledgerBalance();
}
