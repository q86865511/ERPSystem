package com.erp.sales.application;

import com.erp.sales.api.ReceivablesQuery;
import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesInvoiceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-side accounts-receivable reporting. The AR subledger balance is the sum of every open customer
 * invoice's balance; the reconciliation health-check asserts it equals the GL Accounts Receivable (1200)
 * control account. Published to other modules through {@link ReceivablesQuery}.
 */
@Service
@Transactional(readOnly = true)
public class ArSubledgerService implements ReceivablesQuery {

    private final SalesInvoiceRepository salesInvoiceRepository;

    public ArSubledgerService(SalesInvoiceRepository salesInvoiceRepository) {
        this.salesInvoiceRepository = salesInvoiceRepository;
    }

    @Override
    public BigDecimal arSubledgerBalance() {
        return salesInvoiceRepository.findByStatusIn(
                        List.of(SalesInvoiceStatus.POSTED, SalesInvoiceStatus.PARTIALLY_PAID)).stream()
                .map(SalesInvoice::openBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
