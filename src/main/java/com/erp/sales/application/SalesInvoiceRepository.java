package com.erp.sales.application;

import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    Optional<SalesInvoice> findByInvoiceNumber(String invoiceNumber);

    /** Live receivables (POSTED / PARTIALLY_PAID) — the basis of the AR subledger and aging. */
    List<SalesInvoice> findByStatusIn(Collection<SalesInvoiceStatus> statuses);
}
