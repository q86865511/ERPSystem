package com.erp.purchasing.application;

import com.erp.purchasing.domain.VendorBill;
import com.erp.purchasing.domain.VendorBillStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorBillRepository extends JpaRepository<VendorBill, Long> {

    Optional<VendorBill> findByBillNumber(String billNumber);

    /** Bills that still owe money — the basis of the AP subledger balance. */
    List<VendorBill> findByStatusNot(VendorBillStatus status);
}
