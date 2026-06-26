package com.erp.sales.application;

import com.erp.sales.domain.DeliveryLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryLineRepository extends JpaRepository<DeliveryLine, Long> {

    /** Delivery lines for an SO line, oldest first — for FIFO Deferred-COGS matching by an invoice. */
    List<DeliveryLine> findBySoLineIdOrderByIdAsc(Long soLineId);
}
