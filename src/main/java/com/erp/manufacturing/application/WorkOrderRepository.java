package com.erp.manufacturing.application;

import com.erp.manufacturing.domain.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByWoNumber(String woNumber);
}
