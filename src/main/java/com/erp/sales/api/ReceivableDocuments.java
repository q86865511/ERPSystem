package com.erp.sales.api;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The sales module's published port for collecting customer invoices. The payments module allocates a
 * customer receipt against invoices through this interface, never touching sales internals.
 */
public interface ReceivableDocuments {

    Optional<ReceivableInvoiceView> findOpenInvoice(Long invoiceId);

    /** Records an allocated receipt against an invoice, advancing its status. Runs in the caller's tx. */
    void applyReceipt(Long invoiceId, BigDecimal amount);
}
