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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Posts payments. An outgoing payment moves {@code Dr AP / Cr Cash} (the AP line tagged with the
 * vendor) and allocates the amount across the vendor's open bills through the purchasing port, which
 * advances those bills to PARTIALLY_PAID / PAID. One transaction; it takes only the journal sequence
 * lock.
 */
@Service
public class PaymentService {

    private static final String SEQUENCE_SCOPE = "PAYMENT";
    private static final String SOURCE_DOC_TYPE = "PAYMENT";
    private static final String AP_ACCOUNT = "2100";
    private static final String CASH_ACCOUNT = "1010";

    private final PaymentRepository paymentRepository;
    private final SequenceAllocator sequenceAllocator;
    private final LedgerPosting ledgerPosting;
    private final PayableDocuments payableDocuments;

    public PaymentService(PaymentRepository paymentRepository,
                          SequenceAllocator sequenceAllocator,
                          LedgerPosting ledgerPosting,
                          PayableDocuments payableDocuments) {
        this.paymentRepository = paymentRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.ledgerPosting = ledgerPosting;
        this.payableDocuments = payableDocuments;
    }

    public record Allocation(Long billId, BigDecimal amount) {
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

    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
