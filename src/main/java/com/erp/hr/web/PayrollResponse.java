package com.erp.hr.web;

import com.erp.hr.api.PayrollStatus;
import com.erp.hr.domain.Payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API view of a payroll run, including its per-employee lines. */
public record PayrollResponse(Long id, int periodYear, int periodMonth, PayrollStatus status,
                              BigDecimal grossTotal, BigDecimal taxTotal, BigDecimal insuranceTotal,
                              BigDecimal netTotal, LocalDate postingDate, Long journalEntryId,
                              List<PayrollLineResponse> lines) {

    public static PayrollResponse from(Payroll p) {
        return new PayrollResponse(p.getId(), p.getPeriodYear(), p.getPeriodMonth(), p.getStatus(),
                p.getGrossTotal(), p.getTaxTotal(), p.getInsuranceTotal(), p.getNetTotal(),
                p.getPostingDate(), p.getJournalEntryId(),
                p.getLines().stream().map(PayrollLineResponse::from).toList());
    }
}
