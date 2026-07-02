package com.erp.purchasing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Supplier on-time performance: goods-receipt posting date vs the PO line's expected delivery date. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SupplierPerformanceIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private SupplierPerformanceService supplierPerformanceService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    private void receiveOn(Long vendor, Long item, LocalDate expected, LocalDate receiveDate) {
        PurchaseOrder po = purchaseOrderService.createOrder(vendor,
                List.of(new PoLineInput(item, new BigDecimal("10"), new BigDecimal("10"), expected)),
                LocalDate.of(2026, 5, 1), "test");
        purchaseOrderService.confirm(po.getId(), "test");
        Long line = po.getLines().get(0).getId();
        goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(line, new BigDecimal("10"))), receiveDate, "test");
    }

    @Test
    void computesOnTimePercentPerVendor() {
        int n = SEQ.incrementAndGet();
        Long vendor = masterDataService.createPartner("SP-V-" + n, "Supplier " + n, true, false, null,
                30, null, null).getId();
        Long item = masterDataService.createItem("SP-I-" + n, "Item " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();
        LocalDate expected = LocalDate.of(2026, 5, 10);

        receiveOn(vendor, item, expected, LocalDate.of(2026, 5, 8));   // on time
        receiveOn(vendor, item, expected, LocalDate.of(2026, 5, 20));  // late

        SupplierPerformance perf = supplierPerformanceService.performance().stream()
                .filter(p -> p.partnerId().equals(vendor)).findFirst().orElseThrow();
        assertThat(perf.totalReceipts()).isEqualTo(2);
        assertThat(perf.onTime()).isEqualTo(1);
        assertThat(perf.onTimePct()).isEqualByComparingTo("50.0");
    }
}
