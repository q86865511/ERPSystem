package com.erp.hr.application;

import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.PayrollStatus;
import com.erp.hr.domain.Employee;
import com.erp.hr.domain.Payroll;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs payroll and posts it to the general ledger. A run is calculated as a DRAFT from the active
 * employees, then posted as a single balanced journal entry through the ledger's published port.
 */
@Service
public class PayrollService {

    // Flat demo withholding rates (labelled as a simplification, not a real tax/insurance schedule).
    private static final BigDecimal TAX_RATE = new BigDecimal("0.06");
    private static final BigDecimal INSURANCE_RATE = new BigDecimal("0.05");

    private static final String SALARY_EXPENSE = "6100";
    private static final String NET_PAY_PAYABLE = "2200";
    private static final String TAX_PAYABLE = "2210";
    private static final String INSURANCE_PAYABLE = "2220";
    private static final String SOURCE_DOC_TYPE = "PAYROLL";

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final LedgerPosting ledgerPosting;

    public PayrollService(PayrollRepository payrollRepository, EmployeeRepository employeeRepository,
                          LedgerPosting ledgerPosting) {
        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.ledgerPosting = ledgerPosting;
    }

    /** Calculates a DRAFT payroll for the period from the active employees (one line each). */
    @Transactional
    public Payroll run(int year, int month) {
        if (payrollRepository.existsByPeriodYearAndPeriodMonth(year, month)) {
            throw new HrConflictException("payroll already exists for " + year + "-" + month);
        }
        Payroll payroll = new Payroll(year, month);
        for (Employee e : employeeRepository.findByStatusOrderByCode(EmploymentStatus.ACTIVE)) {
            BigDecimal gross = (e.getMonthlySalary() != null ? e.getMonthlySalary() : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = gross.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal insurance = gross.multiply(INSURANCE_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(tax).subtract(insurance);
            payroll.addLine(e.getId(), gross, tax, insurance, net);
        }
        return payrollRepository.saveAndFlush(payroll);
    }

    /** Posts a DRAFT payroll to the ledger as one balanced entry and marks it POSTED. */
    @Transactional
    public Payroll post(Long id, LocalDate postingDate, String actor) {
        Payroll payroll = get(id);
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new HrConflictException("payroll " + id + " is not DRAFT, was " + payroll.getStatus());
        }
        if (payroll.getGrossTotal().signum() <= 0) {
            throw new HrConflictException("cannot post an empty payroll " + id);
        }
        LocalDate date = postingDate != null ? postingDate : LocalDate.now();
        String docId = String.format("PR-%04d-%02d", payroll.getPeriodYear(), payroll.getPeriodMonth());

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(SALARY_EXPENSE, payroll.getGrossTotal(), null, "salaries expense"));
        lines.add(new Line(NET_PAY_PAYABLE, null, payroll.getNetTotal(), "net pay payable"));
        if (payroll.getTaxTotal().signum() > 0) {
            lines.add(new Line(TAX_PAYABLE, null, payroll.getTaxTotal(), "tax withheld"));
        }
        if (payroll.getInsuranceTotal().signum() > 0) {
            lines.add(new Line(INSURANCE_PAYABLE, null, payroll.getInsuranceTotal(), "insurance payable"));
        }

        JournalEntryRequest request = new JournalEntryRequest(null, date, "payroll " + docId, null,
                SOURCE_DOC_TYPE, docId, "POST", lines);
        PostingResult result = ledgerPosting.post(request, actor);
        payroll.markPosted(result.entryId(), date);
        return payrollRepository.saveAndFlush(payroll);
    }

    @Transactional(readOnly = true)
    public List<Payroll> list() {
        return payrollRepository.findByOrderByPeriodYearDescPeriodMonthDesc();
    }

    @Transactional(readOnly = true)
    public Payroll get(Long id) {
        return payrollRepository.findById(id)
                .orElseThrow(() -> new HrNotFoundException("payroll with id " + id));
    }
}
