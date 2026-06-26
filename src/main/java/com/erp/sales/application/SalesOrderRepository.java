package com.erp.sales.application;

import com.erp.sales.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findBySoNumber(String soNumber);
}
