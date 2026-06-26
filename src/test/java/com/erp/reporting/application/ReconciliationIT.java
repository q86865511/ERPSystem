package com.erp.reporting.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillService;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.reporting.application.ReconciliationReport.SubledgerCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the reconciliation health-check. After running a procure-to-pay cycle, the
 * aggregate report is healthy: the trial balance balances and every subledger (inventory, AP, AR)
 * equals its GL control account across the whole accumulated dataset.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReconciliationIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final LocalDate AS_OF = LocalDate.of(2026, 12, 31);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private VendorBillService vendorBillService;
    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    @Test
    void healthCheckIsHealthyAfterAProcureToPayCycle() {
        int n = SEQ.incrementAndGet();
        Long vendorId = masterDataService.createPartner("V-REC2-" + n, "Vendor " + n, true, false, null,
                30, null, null).getId();
        Long itemId = masterDataService.createItem("RM-REC2-" + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("10"), null, null).getId();
        PurchaseOrder po = purchaseOrderService.createOrder(vendorId,
                List.of(new PoLineInput(itemId, new BigDecimal("100"), new BigDecimal("10"))),
                JUNE, "tester");
        purchaseOrderService.confirm(po.getId(), "tester");
        Long poLineId = po.getLines().get(0).getId();
        goodsReceiptService.receive(po.getId(), stockLocationId(),
                List.of(new ReceiptLineInput(poLineId, new BigDecimal("100"))), JUNE, "tester");
        vendorBillService.postBill(po.getId(),
                List.of(new BillLineInput(poLineId, new BigDecimal("100"), new BigDecimal("10"))),
                "STANDARD", JUNE, "tester");

        ReconciliationReport report = reconciliationService.reconcile(AS_OF);

        // Hard invariants hold across the whole dataset.
        assertThat(report.trialBalanceBalanced()).isTrue();
        assertThat(report.subledgerChecks()).allMatch(SubledgerCheck::reconciled);
        assertThat(report.healthy()).isTrue();

        // The AP subledger reconciles to GL 2100, and there is an inventory check.
        SubledgerCheck ap = report.subledgerChecks().stream()
                .filter(c -> c.accountCode().equals("2100")).findFirst().orElseThrow();
        assertThat(ap.reconciled()).isTrue();
        assertThat(report.subledgerChecks()).anyMatch(c -> c.label().equals("Inventory"));
        assertThat(report.clearingAccounts()).extracting(ReconciliationReport.ClearingBalance::accountCode)
                .contains("2150", "1340", "1320");
    }
}
