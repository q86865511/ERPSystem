package com.erp.purchasing.application;

import com.erp.purchasing.domain.GrnLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrnLineRepository extends JpaRepository<GrnLine, Long> {

    /** Receipt lines for a PO line, oldest first — for FIFO GR-IR matching by a vendor bill. */
    List<GrnLine> findByPoLineIdOrderByIdAsc(Long poLineId);
}
