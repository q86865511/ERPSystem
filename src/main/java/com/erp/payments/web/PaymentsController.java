package com.erp.payments.web;

import com.erp.payments.application.PaymentService;
import com.erp.payments.application.PaymentService.Allocation;
import com.erp.payments.application.PaymentService.ReceiptAllocation;
import com.erp.payments.domain.PaymentDirection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for payments: outgoing (vendor) payments and incoming (customer) receipts. */
@RestController
@RequestMapping("/api/payments")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record AllocationLine(@NotNull Long billId, @NotNull BigDecimal amount) {
    }

    // allocations may be empty (an unallocated payment on account) but must not be null.
    public record PayOutRequest(@NotNull Long partnerId, @NotNull BigDecimal amount, LocalDate postingDate,
                                @NotNull @Valid List<AllocationLine> allocations) {
    }

    public record ReceiptAllocationLine(@NotNull Long invoiceId, @NotNull BigDecimal amount) {
    }

    public record PayInRequest(@NotNull Long partnerId, @NotNull BigDecimal amount, LocalDate postingDate,
                               @NotNull @Valid List<ReceiptAllocationLine> allocations) {
    }

    @PostMapping("/out")
    public ResponseEntity<PaymentResponse> payOut(@Valid @RequestBody PayOutRequest request,
                                                  Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        List<Allocation> allocations = request.allocations().stream()
                .map(a -> new Allocation(a.billId(), a.amount())).toList();
        PaymentResponse body = PaymentResponse.from(paymentService.payOut(
                request.partnerId(), request.amount(), postingDate, allocations, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/in")
    public ResponseEntity<PaymentResponse> payIn(@Valid @RequestBody PayInRequest request,
                                                 Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        List<ReceiptAllocation> allocations = request.allocations().stream()
                .map(a -> new ReceiptAllocation(a.invoiceId(), a.amount())).toList();
        PaymentResponse body = PaymentResponse.from(paymentService.payIn(
                request.partnerId(), request.amount(), postingDate, allocations, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return PaymentResponse.from(paymentService.getPayment(id));
    }

    @GetMapping
    public List<PaymentResponse> listPayments(@RequestParam(required = false) PaymentDirection direction) {
        return paymentService.listPayments(direction).stream().map(PaymentResponse::from).toList();
    }
}
