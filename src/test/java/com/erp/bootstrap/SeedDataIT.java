package com.erp.bootstrap;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.reporting.application.ReconciliationReport;
import com.erp.reporting.application.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code seed} profile's {@link DataSeeder} runs the whole buy → make → sell slice through
 * the real posting services and leaves the books reconciled: raw and finished-goods on-hand match the
 * cycle, and the reconciliation health-check is healthy.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("seed")
class SeedDataIT {

    @Autowired
    private MasterDataQuery masterDataQuery;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void seedRunsTheWholeSliceAndReconciles() {
        Long raw = masterDataQuery.findItemBySku("RM-DEMO").orElseThrow().id();
        Long finished = masterDataQuery.findItemBySku("FG-DEMO").orElseThrow().id();

        // Raw: received 100, issued 50 into WIP -> 50 on hand. Finished: produced 50, sold 30 -> 20.
        assertThat(itemCostStateRepository.findById(raw).orElseThrow().getOnHandQty())
                .isEqualByComparingTo("50");
        assertThat(itemCostStateRepository.findById(finished).orElseThrow().getOnHandQty())
                .isEqualByComparingTo("20");

        // After the complete cycle the books reconcile.
        ReconciliationReport report = reconciliationService.reconcile(LocalDate.of(2026, 12, 31));
        assertThat(report.trialBalanceBalanced()).isTrue();
        assertThat(report.healthy()).isTrue();
    }
}
