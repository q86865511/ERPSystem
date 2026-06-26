package com.erp.ledger.application;

import com.erp.ledger.domain.FiscalPeriod;
import com.erp.ledger.domain.FiscalYear;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-close of fiscal periods: closing a period blocks new postings into it (the posting service refuses
 * any entry whose date falls in a non-OPEN period); reopening restores it. Hard close (LOCKED, blocking
 * even reversals) and retained-earnings carry-forward are reserved for a later phase.
 */
@Service
public class FiscalPeriodService {

    private final FiscalYearRepository fiscalYearRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;

    public FiscalPeriodService(FiscalYearRepository fiscalYearRepository,
                               FiscalPeriodRepository fiscalPeriodRepository) {
        this.fiscalYearRepository = fiscalYearRepository;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
    }

    @Transactional
    public FiscalPeriod close(String yearCode, int periodNo) {
        FiscalPeriod period = resolve(yearCode, periodNo);
        period.close();
        return fiscalPeriodRepository.saveAndFlush(period);
    }

    @Transactional
    public FiscalPeriod reopen(String yearCode, int periodNo) {
        FiscalPeriod period = resolve(yearCode, periodNo);
        period.reopen();
        return fiscalPeriodRepository.saveAndFlush(period);
    }

    @Transactional(readOnly = true)
    public FiscalPeriod getPeriod(String yearCode, int periodNo) {
        return resolve(yearCode, periodNo);
    }

    private FiscalPeriod resolve(String yearCode, int periodNo) {
        FiscalYear year = fiscalYearRepository.findByCode(yearCode)
                .orElseThrow(() -> new InvalidLedgerRequestException("unknown fiscal year: " + yearCode));
        return fiscalPeriodRepository.findByFiscalYearIdAndPeriodNo(year.getId(), periodNo)
                .orElseThrow(() -> new InvalidLedgerRequestException(
                        "unknown fiscal period: " + yearCode + "/" + periodNo));
    }
}
