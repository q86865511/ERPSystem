package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.manufacturing.application.ReorderReport.ReorderItem;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the reorder-point report: items at or below their reorder point are listed (with
 * the suggested reorder quantity); items above it, or with no reorder point, are not.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReorderReportIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private ReorderReportService reorderReportService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    private Long stockedItem(String onHand, BigDecimal reorderPoint, BigDecimal reorderQty) {
        int n = SEQ.incrementAndGet();
        Long itemId = masterDataService.createItem("RM-RO-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), reorderPoint, reorderQty).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal(onHand),
                new BigDecimal("10"), "seed", JUNE, "tester");
        return itemId;
    }

    @Test
    void listsItemsBelowTheirReorderPoint() {
        Long low = stockedItem("10", new BigDecimal("20"), new BigDecimal("100"));   // below point
        Long healthy = stockedItem("50", new BigDecimal("5"), new BigDecimal("50")); // above point
        Long noPoint = stockedItem("1", null, null);                                  // no reorder point

        var items = reorderReportService.reorderReport().items();

        ReorderItem lowItem = items.stream().filter(i -> i.itemId().equals(low)).findFirst().orElseThrow();
        assertThat(lowItem.onHandQty()).isEqualByComparingTo("10");
        assertThat(lowItem.reorderPoint()).isEqualByComparingTo("20");
        assertThat(lowItem.reorderQty()).isEqualByComparingTo("100");

        assertThat(items).noneMatch(i -> i.itemId().equals(healthy));
        assertThat(items).noneMatch(i -> i.itemId().equals(noPoint));
    }
}
