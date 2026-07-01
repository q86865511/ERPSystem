package com.erp.hr.web;

import com.erp.hr.domain.PayrollLine;

import java.math.BigDecimal;

/** API view of one employee's payroll line (payslip). */
public record PayrollLineResponse(Long id, Long employeeId, BigDecimal gross, BigDecimal tax,
                                  BigDecimal insurance, BigDecimal net) {

    public static PayrollLineResponse from(PayrollLine l) {
        return new PayrollLineResponse(l.getId(), l.getEmployeeId(), l.getGross(), l.getTax(),
                l.getInsurance(), l.getNet());
    }
}
