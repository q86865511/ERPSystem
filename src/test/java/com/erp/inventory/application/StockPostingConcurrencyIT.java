package com.erp.inventory.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.domain.ItemCostState;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for the moving-average posting path — the first in the repo. Fires concurrent gains
 * on the same item; the pessimistic lock on {@code item_cost_state} (taken before the journal-entry
 * sequence) must serialize them so the final on-hand and average are exactly the serial result, with no
 * deadlock. Validates the fixed lock order.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StockPostingConcurrencyIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final int THREADS = 8;

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentGainsOnSameItemSerializeCorrectly() throws Exception {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        Long stock = locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
        Long itemId = masterDataService.createItem("CONC-1", "Concurrent", ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    stockAdjustmentService.postAdjustment(itemId, stock, new BigDecimal("10"),
                            new BigDecimal("10"), "concurrent gain", JUNE, "tester");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS); // throws if any worker failed or it deadlocked
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        // Serial expectation: 8 × 10 units @ 10 → 80 on hand, value 800, average 10.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("80");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("800");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");

        // Cache equals the source-of-truth STOCK-leg sum (no lost updates).
        BigDecimal stockLegSum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(sle.value_delta), 0) FROM stock_ledger_entry sle "
                        + "JOIN location l ON l.id = sle.location_id "
                        + "WHERE l.location_type = 'STOCK' AND sle.item_id = ?",
                BigDecimal.class, itemId);
        assertThat(stockLegSum).isEqualByComparingTo("800");
    }
}
