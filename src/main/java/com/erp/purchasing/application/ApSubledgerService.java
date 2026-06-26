package com.erp.purchasing.application;

import com.erp.purchasing.api.PayablesQuery;
import com.erp.purchasing.domain.VendorBill;
import com.erp.purchasing.domain.VendorBillStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Read-side accounts-payable reporting. The AP subledger balance is the sum of every open vendor bill's
 * balance; the reconciliation health-check asserts it equals the GL Accounts Payable (2100) control
 * account. Published to other modules through {@link PayablesQuery}.
 */
@Service
@Transactional(readOnly = true)
public class ApSubledgerService implements PayablesQuery {

    private final VendorBillRepository vendorBillRepository;

    public ApSubledgerService(VendorBillRepository vendorBillRepository) {
        this.vendorBillRepository = vendorBillRepository;
    }

    @Override
    public BigDecimal apSubledgerBalance() {
        return vendorBillRepository.findByStatusNot(VendorBillStatus.PAID).stream()
                .map(VendorBill::openBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
