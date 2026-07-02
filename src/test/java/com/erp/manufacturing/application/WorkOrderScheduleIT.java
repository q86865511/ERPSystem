package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.domain.WorkOrder;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.application.MasterDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Work orders can carry a planned production window, surfaced to the manufacturing schedule Gantt. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkOrderScheduleIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private BomService bomService;
    @Autowired
    private WorkOrderService workOrderService;

    @Test
    void createWithScheduleStoresThePlannedWindow() {
        int n = SEQ.incrementAndGet();
        Long finished = masterDataService.createItem("SCH-FG-" + n, "FG " + n, ItemType.FINISHED, "EA",
                true, BigDecimal.ZERO, null, null).getId();
        Long raw = masterDataService.createItem("SCH-RM-" + n, "RM " + n, ItemType.RAW, "EA", true,
                new BigDecimal("5"), null, null).getId();
        Long bomId = bomService.createBom(finished, BigDecimal.ONE,
                List.of(new ComponentInput(raw, BigDecimal.ONE, null)), "test").getId();

        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 8);
        WorkOrder wo = workOrderService.create(finished, bomId, new BigDecimal("10"), start, end, "test");

        assertThat(wo.getPlannedStart()).isEqualTo(start);
        assertThat(wo.getPlannedEnd()).isEqualTo(end);
        assertThat(workOrderService.listWorkOrders())
                .anyMatch(w -> w.getId().equals(wo.getId()) && start.equals(w.getPlannedStart()));
    }
}
