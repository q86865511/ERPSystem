package com.erp.purchasing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.purchasing.domain.VendorBill;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards ADR 0003's fixed lock order on the multi-item posting path. A vendor bill with price variance
 * on several items locks one {@code item_cost_state} row per item; if the order followed the bill lines,
 * two bills listing the same items in opposite order would deadlock (SQLSTATE 40P01). The first test
 * pins the ordering itself, the second runs the opposite-order pair concurrently.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VendorBillLockOrderIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int ROUNDS = 4;

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private GoodsReceiptService goodsReceiptService;
    @Autowired
    private VendorBillService vendorBillService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    private Long createItem(String prefix) {
        int n = SEQ.incrementAndGet();
        return masterDataService.createItem(prefix + n, "Raw " + n, ItemType.RAW, "EA", true,
                new BigDecimal("100"), null, null).getId();
    }

    /** A confirmed + fully received two-line PO for the given items. Returns its two po-line ids. */
    private long[] receivedTwoLineOrder(Long vendorId, Long firstItem, Long secondItem) {
        PurchaseOrder po = purchaseOrderService.createOrder(vendorId, List.of(
                new PoLineInput(firstItem, new BigDecimal("10"), new BigDecimal("100")),
                new PoLineInput(secondItem, new BigDecimal("10"), new BigDecimal("100"))),
                JUNE, "tester");
        Long first = po.getLines().get(0).getId();
        Long second = po.getLines().get(1).getId();
        purchaseOrderService.confirm(po.getId(), "tester");
        goodsReceiptService.receive(po.getId(), stockLocationId(), List.of(
                new ReceiptLineInput(first, new BigDecimal("10")),
                new ReceiptLineInput(second, new BigDecimal("10"))), JUNE, "tester");
        return new long[]{po.getId(), first, second};
    }

    /** Bills both lines above receipt cost, so every item carries a variance and takes its cost lock. */
    private VendorBill postVariancedBill(long[] order, boolean firstLineFirst) {
        List<BillLineInput> lines = firstLineFirst
                ? List.of(new BillLineInput(order[1], new BigDecimal("10"), new BigDecimal("110")),
                          new BillLineInput(order[2], new BigDecimal("10"), new BigDecimal("110")))
                : List.of(new BillLineInput(order[2], new BigDecimal("10"), new BigDecimal("110")),
                          new BillLineInput(order[1], new BigDecimal("10"), new BigDecimal("110")));
        return vendorBillService.postBill(order[0], lines, "STANDARD", JUNE, "tester");
    }

    @Test
    void costStateLocksAreTakenInItemIdOrderWhateverTheBillLineOrder() {
        Long vendorId = masterDataService.createPartner("V-LOCK-" + SEQ.incrementAndGet(), "Vendor lock",
                true, false, null, 30, null, null).getId();
        Long lowItem = createItem("RM-LOCK-A-");
        Long highItem = createItem("RM-LOCK-B-");
        assertThat(highItem).isGreaterThan(lowItem);

        // PO (and therefore bill) lines in descending item id — the reverse of the lock order.
        long[] order = receivedTwoLineOrder(vendorId, highItem, lowItem);
        VendorBill bill = postVariancedBill(order, true);

        // Each revaluation takes that item's cost-state lock, so the leg order is the lock order.
        List<Long> revaluedItems = jdbcTemplate.queryForList(
                "SELECT item_id FROM stock_ledger_entry WHERE source_doc_type = 'VENDOR_BILL' "
                        + "AND source_doc_id = ? ORDER BY id", Long.class, bill.getBillNumber());
        assertThat(revaluedItems).containsExactly(lowItem, highItem);
    }

    @Test
    void concurrentBillsCoveringTheSameItemsInOppositeLineOrderDoNotDeadlock() throws Exception {
        Long vendorId = masterDataService.createPartner("V-DLK-" + SEQ.incrementAndGet(), "Vendor dlk",
                true, false, null, 30, null, null).getId();
        Long itemA = createItem("RM-DLK-A-");
        Long itemB = createItem("RM-DLK-B-");

        List<long[]> forward = new ArrayList<>();
        List<long[]> reverse = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            forward.add(receivedTwoLineOrder(vendorId, itemA, itemB));
            reverse.add(receivedTwoLineOrder(vendorId, itemB, itemA));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < ROUNDS; i++) {
                long[] ascending = forward.get(i);
                long[] descending = reverse.get(i);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = List.of(
                        pool.submit(() -> {
                            start.await();
                            return postVariancedBill(ascending, true);
                        }),
                        pool.submit(() -> {
                            start.await();
                            return postVariancedBill(descending, true);
                        }));
                start.countDown();
                for (Future<?> future : futures) {
                    // Throws on a 40P01 deadlock abort (or any other failure) instead of hanging.
                    assertThat(future.get(30, TimeUnit.SECONDS)).isNotNull();
                }
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        // Every bill posted: 2 items × 2 bills per round, each revaluing once.
        Integer revalueLegs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_ledger_entry WHERE source_doc_type = 'VENDOR_BILL' "
                        + "AND item_id IN (?, ?)", Integer.class, itemA, itemB);
        assertThat(revalueLegs).isEqualTo(ROUNDS * 4);
    }
}
