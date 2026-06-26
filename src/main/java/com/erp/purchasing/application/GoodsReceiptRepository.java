package com.erp.purchasing.application;

import com.erp.purchasing.domain.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    Optional<GoodsReceipt> findByGrnNumber(String grnNumber);
}
