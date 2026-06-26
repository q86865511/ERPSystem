package com.erp.sales.application;

import com.erp.TestcontainersConfiguration;
import com.erp.inventory.application.ItemCostStateRepository;
import com.erp.inventory.application.StockAdjustmentService;
import com.erp.inventory.domain.ItemCostState;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.Delivery;
import com.erp.sales.domain.SalesOrder;
import com.erp.sales.domain.SalesOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Posting-rule integration test for deliveries. Asserts a delivery posts Dr Deferred-COGS (1340) /
 * Cr Finished Goods (1330) through the inventory port at moving-average cost, lowers the on-hand
 * quantity, records the delivery cost, and advances the sales order's shipped quantity and status.
 * Unique customer/item codes per test (the container is shared per class).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeliveryPostingIT {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private StockAdjustmentService stockAdjustmentService;
    @Autowired
    private SalesOrderService salesOrderService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private ItemCostStateRepository itemCostStateRepository;
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

    private Long newCustomer() {
        int n = SEQ.incrementAndGet();
        return masterDataService.createPartner("C-DLV-" + n, "Customer " + n, false, true, null, 30,
                null, null).getId();
    }

    /** A finished good with {@code onHand} units of stock seeded at {@code cost} via an adjustment. */
    private Long stockedFinishedItem(String onHand, String cost) {
        int n = SEQ.incrementAndGet();
        Long itemId = masterDataService.createItem("FG-DLV-" + n, "Finished " + n, ItemType.FINISHED,
                "EA", true, new BigDecimal(cost), null, null).getId();
        stockAdjustmentService.postAdjustment(itemId, stockLocationId(), new BigDecimal(onHand),
                new BigDecimal(cost), "seed stock", JUNE, "tester");
        return itemId;
    }

    private SalesOrder confirmedOrder(Long customerId, Long itemId, String qty, String price) {
        SalesOrder so = salesOrderService.createOrder(customerId,
                List.of(new SoLineInput(itemId, new BigDecimal(qty), new BigDecimal(price))),
                JUNE, "tester");
        return salesOrderService.confirm(so.getId(), "tester");
    }

    @Test
    void deliveryPostsDeferredCogsAgainstInventoryAndAdvancesOrder() {
        Long itemId = stockedFinishedItem("50", "10");
        SalesOrder so = confirmedOrder(newCustomer(), itemId, "30", "20");
        Long soLineId = so.getLines().get(0).getId();

        Delivery delivery = deliveryService.deliver(so.getId(), stockLocationId(),
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("30"))), JUNE, "tester");

        // Dr 1340 Deferred-COGS / Cr 1330 Finished Goods for 300 (30 @ avg cost 10).
        Long jeId = delivery.getLines().get(0).getJournalEntryId();
        assertThat(jeId).isNotNull();
        assertThat(debitFor(jeId, "1340")).isEqualByComparingTo("300");
        assertThat(creditFor(jeId, "1330")).isEqualByComparingTo("300");

        // Delivery line records the cost it shipped at.
        assertThat(delivery.getLines().get(0).getUnitCost()).isEqualByComparingTo("10");

        // Finished-goods moving-average cost state dropped to 20 @ 10.
        ItemCostState cost = itemCostStateRepository.findById(itemId).orElseThrow();
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("20");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("200");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");

        // Order fully shipped.
        SalesOrder reloaded = salesOrderRepository.findById(so.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SalesOrderStatus.SHIPPED);
        assertThat(reloaded.getLines().get(0).getQtyShipped()).isEqualByComparingTo("30");
    }

    @Test
    void partialDeliveriesAdvanceStatusIncrementally() {
        Long itemId = stockedFinishedItem("100", "5");
        SalesOrder so = confirmedOrder(newCustomer(), itemId, "100", "8");
        Long soLineId = so.getLines().get(0).getId();
        Long stock = stockLocationId();

        deliveryService.deliver(so.getId(), stock,
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("40"))), JUNE, "tester");
        assertThat(salesOrderRepository.findById(so.getId()).orElseThrow().getStatus())
                .isEqualTo(SalesOrderStatus.PARTIALLY_SHIPPED);

        deliveryService.deliver(so.getId(), stock,
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("60"))), JUNE, "tester");
        assertThat(salesOrderRepository.findById(so.getId()).orElseThrow().getStatus())
                .isEqualTo(SalesOrderStatus.SHIPPED);
    }

    @Test
    void blocksOverDelivery() {
        Long itemId = stockedFinishedItem("100", "10");
        SalesOrder so = confirmedOrder(newCustomer(), itemId, "30", "20");
        Long soLineId = so.getLines().get(0).getId();

        assertThatThrownBy(() -> deliveryService.deliver(so.getId(), stockLocationId(),
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("31"))), JUNE, "tester"))
                .isInstanceOf(SalesValidationException.class);
    }

    @Test
    void blocksDeliveryBeyondOnHand() {
        // Ordered 50 but only 30 in stock: the inventory port must refuse to go negative.
        Long itemId = stockedFinishedItem("30", "10");
        SalesOrder so = confirmedOrder(newCustomer(), itemId, "50", "20");
        Long soLineId = so.getLines().get(0).getId();

        assertThatThrownBy(() -> deliveryService.deliver(so.getId(), stockLocationId(),
                List.of(new DeliveryLineInput(soLineId, new BigDecimal("50"))), JUNE, "tester"))
                .isInstanceOf(RuntimeException.class);
    }

    private BigDecimal debitFor(Long journalEntryId, String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.debit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE jl.journal_entry_id = ? AND a.code = ?",
                BigDecimal.class, journalEntryId, accountCode);
    }

    private BigDecimal creditFor(Long journalEntryId, String accountCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(jl.credit), 0) FROM journal_line jl "
                        + "JOIN account a ON a.id = jl.account_id "
                        + "WHERE jl.journal_entry_id = ? AND a.code = ?",
                BigDecimal.class, journalEntryId, accountCode);
    }
}
