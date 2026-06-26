package com.erp.purchasing.api;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The purchasing module's published port for paying down vendor bills. The payments module allocates a
 * payment against bills through this interface, never touching purchasing internals.
 */
public interface PayableDocuments {

    Optional<PayableBillView> findOpenBill(Long billId);

    /** Records an allocated payment against a bill, advancing its status. Runs in the caller's tx. */
    void applyPayment(Long billId, BigDecimal amount);
}
