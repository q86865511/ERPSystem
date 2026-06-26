package com.erp.payments.web;

import com.erp.payments.domain.Payment;
import com.erp.payments.domain.PaymentAllocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API view of a posted payment and its allocations. */
public record PaymentResponse(
        Long id,
        String payNumber,
        String direction,
        Long partnerId,
        BigDecimal amount,
        LocalDate postingDate,
        String status,
        Long journalEntryId,
        Instant postedAt,
        List<AllocationView> allocations) {

    public record AllocationView(Long id, Long documentId, BigDecimal amount) {
    }

    public static PaymentResponse from(Payment payment) {
        List<AllocationView> allocations = payment.getAllocations().stream()
                .map(PaymentResponse::toView).toList();
        return new PaymentResponse(payment.getId(), payment.getPayNumber(),
                payment.getDirection().name(), payment.getPartnerId(), payment.getAmount(),
                payment.getPostingDate(), payment.getStatus().name(), payment.getJournalEntryId(),
                payment.getPostedAt(), allocations);
    }

    private static AllocationView toView(PaymentAllocation allocation) {
        return new AllocationView(allocation.getId(), allocation.getDocumentId(), allocation.getAmount());
    }
}
