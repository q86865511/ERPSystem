package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.manufacturing.domain.Equipment;
import com.erp.manufacturing.domain.ProductionLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** OEE = Availability × Performance × Quality, aggregated from production logs; plus downtime-by-reason. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OeeIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private ProductionLogRepository productionLogRepository;
    @Autowired
    private OeeService oeeService;

    @Test
    void computesOeeComponentsAndDowntime() {
        int n = SEQ.incrementAndGet();
        Equipment equipment = equipmentRepository.saveAndFlush(
                new Equipment("OEE-EQ-" + n, "Machine " + n, new BigDecimal("30")));
        // planned 480, downtime 60 -> runtime 420. ideal = 30 * 420/60 = 210. produced 189, good 180.
        productionLogRepository.saveAndFlush(new ProductionLog(equipment.getId(),
                LocalDate.of(2026, 6, 1), 480, 60, "Setup", 189, 180));

        EquipmentOee oee = oeeService.oee().stream()
                .filter(o -> o.equipmentId().equals(equipment.getId())).findFirst().orElseThrow();
        assertThat(oee.availability()).isEqualByComparingTo("87.5");  // 420/480
        assertThat(oee.performance()).isEqualByComparingTo("90.0");   // 189/210
        assertThat(oee.quality()).isEqualByComparingTo("95.2");       // 180/189
        assertThat(oee.oee()).isGreaterThan(new BigDecimal("70")).isLessThan(new BigDecimal("80"));

        assertThat(oeeService.downtime())
                .anyMatch(dt -> dt.reason().equals("Setup") && dt.minutes() >= 60);
    }
}
