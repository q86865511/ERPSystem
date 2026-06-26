package com.erp.inventory.application;

import com.erp.inventory.domain.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Optional<StockAdjustment> findByAdjustmentNumber(String adjustmentNumber);
}
