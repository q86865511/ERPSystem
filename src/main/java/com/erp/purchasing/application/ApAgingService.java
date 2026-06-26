package com.erp.purchasing.application;

import com.erp.masterdata.api.MasterDataQuery;
import com.erp.masterdata.api.PartnerView;
import com.erp.purchasing.domain.VendorBill;
import com.erp.purchasing.domain.VendorBillStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Read-side accounts-payable aging: open bills bucketed by days past their due date. */
@Service
@Transactional(readOnly = true)
public class ApAgingService {

    private static final int DEFAULT_TERMS_DAYS = 30;

    private final VendorBillRepository vendorBillRepository;
    private final MasterDataQuery masterDataQuery;

    public ApAgingService(VendorBillRepository vendorBillRepository, MasterDataQuery masterDataQuery) {
        this.vendorBillRepository = vendorBillRepository;
        this.masterDataQuery = masterDataQuery;
    }

    public ApAgingReport apAging(LocalDate asOf) {
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d1to30 = BigDecimal.ZERO;
        BigDecimal d31to60 = BigDecimal.ZERO;
        BigDecimal d61to90 = BigDecimal.ZERO;
        BigDecimal d90plus = BigDecimal.ZERO;

        for (VendorBill bill : vendorBillRepository.findByStatusNot(VendorBillStatus.PAID)) {
            BigDecimal open = bill.openBalance();
            if (open.signum() <= 0) {
                continue;
            }
            int terms = masterDataQuery.findPartner(bill.getPartnerId())
                    .map(PartnerView::paymentTermsDays).orElse(DEFAULT_TERMS_DAYS);
            LocalDate dueDate = bill.getPostingDate().plusDays(terms);
            long overdue = ChronoUnit.DAYS.between(dueDate, asOf);
            if (overdue <= 0) {
                current = current.add(open);
            } else if (overdue <= 30) {
                d1to30 = d1to30.add(open);
            } else if (overdue <= 60) {
                d31to60 = d31to60.add(open);
            } else if (overdue <= 90) {
                d61to90 = d61to90.add(open);
            } else {
                d90plus = d90plus.add(open);
            }
        }
        BigDecimal total = current.add(d1to30).add(d31to60).add(d61to90).add(d90plus);
        return new ApAgingReport(current, d1to30, d31to60, d61to90, d90plus, total);
    }
}
