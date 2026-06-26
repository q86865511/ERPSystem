package com.erp.payments.web;

import com.erp.payments.application.PaymentService;
import com.erp.payments.application.PaymentService.Allocation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for payments. Phase 2 exposes outgoing (vendor) payments. */
@RestController
@RequestMapping("/api/payments")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record AllocationLine(Long billId, BigDecimal amount) {
    }

    public record PayOutRequest(Long partnerId, BigDecimal amount, LocalDate postingDate,
                                List<AllocationLine> allocations) {
    }

    @PostMapping("/out")
    public ResponseEntity<PaymentResponse> payOut(@RequestBody PayOutRequest request,
                                                  Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        List<Allocation> allocations = request.allocations().stream()
                .map(a -> new Allocation(a.billId(), a.amount())).toList();
        PaymentResponse body = PaymentResponse.from(paymentService.payOut(
                request.partnerId(), request.amount(), postingDate, allocations, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return PaymentResponse.from(paymentService.getPayment(id));
    }
}
