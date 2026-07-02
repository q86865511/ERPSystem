package com.erp.inventory.application;

import com.erp.TestcontainersConfiguration;
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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-item status (value + OUT/LOW/OK) for the inventory heat treemap. Shares the container (unique codes). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ItemsStatusIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private InventoryReportService inventoryReportService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    private Long stockedItem(String reorderPoint, String onHand, String cost) {
        int n = SEQ.incrementAndGet();
        Long itemId = masterDataService.createItem("IS-" + n, "Item " + n, ItemType.RAW, "EA", true,
                new BigDecimal(cost), reorderPoint == null ? null : new BigDecimal(reorderPoint), null).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal(onHand),
                new BigDecimal(cost), "seed", JUNE, "tester");
        return itemId;
    }

    @Test
    void classifiesStatusAndSortsByValue() {
        Long low = stockedItem("20", "10", "5");   // on-hand 10 <= reorder 20 -> LOW, value 50
        Long ok = stockedItem("5", "50", "3");      // on-hand 50 > reorder 5  -> OK,  value 150

        List<ItemStatus> statuses = inventoryReportService.itemsStatus();

        ItemStatus lowItem = statuses.stream().filter(s -> s.itemId().equals(low)).findFirst().orElseThrow();
        assertThat(lowItem.status()).isEqualTo("LOW");
        assertThat(lowItem.value()).isEqualByComparingTo("50");

        ItemStatus okItem = statuses.stream().filter(s -> s.itemId().equals(ok)).findFirst().orElseThrow();
        assertThat(okItem.status()).isEqualTo("OK");
        assertThat(okItem.value()).isEqualByComparingTo("150");

        assertThat(statuses).isSortedAccordingTo(Comparator.comparing(ItemStatus::value).reversed());
    }
}
