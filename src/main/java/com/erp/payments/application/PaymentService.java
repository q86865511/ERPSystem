package com.erp.payments.application;

import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.payments.domain.Payment;
import com.erp.payments.domain.PaymentDirection;
import com.erp.purchasing.api.PayableBillView;
import com.erp.purchasing.api.PayableDocuments;
import com.erp.sales.api.ReceivableDocuments;
import com.erp.sales.api.ReceivableInvoiceView;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Posts payments. An outgoing (vendor) payment moves {@code Dr AP / Cr Cash} and settles bills through
 * the purchasing port; an incoming (customer) receipt moves {@code Dr Cash / Cr AR} and settles invoices
 * through the sales port — both with the AP/AR line tagged with the partner, advancing the documents to
 * PARTIALLY_PAID / PAID. One transaction; it takes only the journal sequence lock.
 */
@Service
public class PaymentService {

    private static final String SEQUENCE_SCOPE = "PAYMENT";
    private static final String SOURCE_DOC_TYPE = "PAYMENT";
    private static final String AP_ACCOUNT = "2100";
    private static final String AR_ACCOUNT = "1200";
    private static final String CASH_ACCOUNT = "1010";

    private final PaymentRepository paymentRepository;
    private final SequenceAllocator sequenceAllocator;
    private final LedgerPosting ledgerPosting;
    private final PayableDocuments payableDocuments;
    private final ReceivableDocuments receivableDocuments;

    public PaymentService(PaymentRepository paymentRepository,
                          SequenceAllocator sequenceAllocator,
                          LedgerPosting ledgerPosting,
                          PayableDocuments payableDocuments,
                          ReceivableDocuments receivableDocuments) {
        this.paymentRepository = paymentRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.ledgerPosting = ledgerPosting;
        this.payableDocuments = payableDocuments;
        this.receivableDocuments = receivableDocuments;
    }

    public record Allocation(Long billId, BigDecimal amount) {
    }

    public record ReceiptAllocation(Long invoiceId, BigDecimal amount) {
    }

    @Transactional
    public Payment payOut(Long partnerId, BigDecimal amount, LocalDate postingDate,
                          List<Allocation> allocations, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentValidationException("payment amount must be positive");
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new PaymentValidationException("a payment needs at least one allocation");
        }
        BigDecimal allocated = allocations.stream().map(Allocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(amount) != 0) {
            throw new PaymentValidationException(
                    "allocations (" + allocated + ") must sum to the payment amount (" + amount + ")");
        }
        for (Allocation allocation : allocations) {
            PayableBillView bill = payableDocuments.findOpenBill(allocation.billId())
                    .orElseThrow(() -> new PaymentValidationException(
                            "unknown bill " + allocation.billId()));
            if (!bill.partnerId().equals(partnerId)) {
                throw new PaymentValidationException(
                        "bill " + allocation.billId() + " belongs to a different partner");
            }
        }

        String payNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        Payment payment = new Payment(payNumber, PaymentDirection.OUT, partnerId, amount, postingDate);

        JournalEntryRequest request = new JournalEntryRequest(null, postingDate,
                "payment " + payNumber, null, SOURCE_DOC_TYPE, payNumber, "PAYMENT",
                List.of(new Line(AP_ACCOUNT, amount, null, "accounts payable", partnerId),
                        new Line(CASH_ACCOUNT, null, amount, "cash", null)));
        PostingResult posting = ledgerPosting.post(request, actor);

        for (Allocation allocation : allocations) {
            payableDocuments.applyPayment(allocation.billId(), allocation.amount());
            payment.addAllocation(allocation.billId(), allocation.amount());
        }
        payment.linkJournalEntry(posting.entryId());
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment payIn(Long partnerId, BigDecimal amount, LocalDate postingDate,
                         List<ReceiptAllocation> allocations, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentValidationException("receipt amount must be positive");
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new PaymentValidationException("a receipt needs at least one allocation");
        }
        BigDecimal allocated = allocations.stream().map(ReceiptAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(amount) != 0) {
            throw new PaymentValidationException(
                    "allocations (" + allocated + ") must sum to the receipt amount (" + amount + ")");
        }
        for (ReceiptAllocation allocation : allocations) {
            ReceivableInvoiceView invoice = receivableDocuments.findOpenInvoice(allocation.invoiceId())
                    .orElseThrow(() -> new PaymentValidationException(
                            "unknown invoice " + allocation.invoiceId()));
            if (!invoice.partnerId().equals(partnerId)) {
                throw new PaymentValidationException(
                        "invoice " + allocation.invoiceId() + " belongs to a different partner");
            }
        }

        String payNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        Payment payment = new Payment(payNumber, PaymentDirection.IN, partnerId, amount, postingDate);

        JournalEntryRequest request = new JournalEntryRequest(null, postingDate,
                "receipt " + payNumber, null, SOURCE_DOC_TYPE, payNumber, "PAYMENT",
                List.of(new Line(CASH_ACCOUNT, amount, null, "cash", null),
                        new Line(AR_ACCOUNT, null, amount, "accounts receivable", partnerId)));
        PostingResult posting = ledgerPosting.post(request, actor);

        for (ReceiptAllocation allocation : allocations) {
            receivableDocuments.applyReceipt(allocation.invoiceId(), allocation.amount());
            payment.addAllocation(allocation.invoiceId(), allocation.amount());
        }
        payment.linkJournalEntry(posting.entryId());
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    /** All payments, optionally filtered by direction (IN = receipts, OUT = vendor payments). */
    @Transactional(readOnly = true)
    public List<Payment> listPayments(PaymentDirection direction) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        return direction != null
                ? paymentRepository.findByDirection(direction, sort)
                : paymentRepository.findAll(sort);
    }
}
