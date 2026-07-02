package com.erp.manufacturing.application;

import com.erp.manufacturing.domain.ProductionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProductionLogRepository extends JpaRepository<ProductionLog, Long> {

    List<ProductionLog> findByEquipmentId(Long equipmentId);

    boolean existsByEquipmentIdAndLogDate(Long equipmentId, LocalDate logDate);
}
