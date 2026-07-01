package com.erp.hr.web;

import com.erp.hr.application.PayrollService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for running payroll and posting it to the ledger. */
@RestController
@RequestMapping("/api/hr/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    public record RunPayrollRequest(Integer year, Integer month) {
    }

    public record PostPayrollRequest(LocalDate postingDate) {
    }

    @PostMapping
    public ResponseEntity<PayrollResponse> run(@RequestBody RunPayrollRequest request) {
        LocalDate today = LocalDate.now();
        int year = request.year() != null ? request.year() : today.getYear();
        int month = request.month() != null ? request.month() : today.getMonthValue();
        PayrollResponse body = PayrollResponse.from(payrollService.run(year, month));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/post")
    public PayrollResponse post(@PathVariable Long id,
                                @RequestBody(required = false) PostPayrollRequest request,
                                Principal principal) {
        LocalDate postingDate = request != null ? request.postingDate() : null;
        String actor = principal != null ? principal.getName() : "system";
        return PayrollResponse.from(payrollService.post(id, postingDate, actor));
    }

    @GetMapping
    public List<PayrollResponse> list() {
        return payrollService.list().stream().map(PayrollResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PayrollResponse get(@PathVariable Long id) {
        return PayrollResponse.from(payrollService.get(id));
    }
}
