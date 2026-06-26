package com.erp.masterdata.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.ItemView;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the masterdata read port against a real PostgreSQL (Testcontainers). Asserts
 * that the Flyway-seeded posting rules resolve to the right ledger account codes and that item/location
 * lookups map to the published views. Each test that creates an item uses a unique sku (tests commit
 * and share the container per class).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MasterDataQueryServiceIT {

    @Autowired
    private MasterDataQuery masterDataQuery;
    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void resolvesInventoryControlAccountByItemType() {
        assertThat(masterDataQuery.resolveInventoryAccountCode(ItemType.RAW)).isEqualTo("1310");
        assertThat(masterDataQuery.resolveInventoryAccountCode(ItemType.WIP)).isEqualTo("1320");
        assertThat(masterDataQuery.resolveInventoryAccountCode(ItemType.FINISHED)).isEqualTo("1330");
    }

    @Test
    void resolvesCounterAccountByMovementType() {
        assertThat(masterDataQuery.resolveCounterAccountCode(InventoryMovementType.ADJUSTMENT_IN))
                .isEqualTo("6000");
        assertThat(masterDataQuery.resolveCounterAccountCode(InventoryMovementType.ADJUSTMENT_OUT))
                .isEqualTo("6000");
    }

    @Test
    void rejectsItemTypeWithNoInventoryRule() {
        assertThatThrownBy(() -> masterDataQuery.resolveInventoryAccountCode(ItemType.SERVICE))
                .isInstanceOf(PostingRuleNotFoundException.class);
    }

    @Test
    void rejectsMovementTypeWithNoCounterRule() {
        assertThatThrownBy(() -> masterDataQuery.resolveCounterAccountCode(InventoryMovementType.RECEIPT))
                .isInstanceOf(PostingRuleNotFoundException.class);
    }

    @Test
    void findsSeededInventoryLossAndStockLocations() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();

        LocationView loss = masterDataQuery.findLocationByType(warehouseId, LocationType.INVENTORY_LOSS)
                .orElseThrow();
        assertThat(loss.type()).isEqualTo(LocationType.INVENTORY_LOSS);
        assertThat(loss.warehouseId()).isEqualTo(warehouseId);

        LocationView stock = masterDataQuery.findLocationByType(warehouseId, LocationType.STOCK)
                .orElseThrow();
        assertThat(stock.type()).isEqualTo(LocationType.STOCK);
    }

    @Test
    void createsAndReadsBackItem() {
        var created = masterDataService.createItem("RM-IT-1", "Raw IT 1", ItemType.RAW, "EA", true,
                new BigDecimal("12.500000"), null, null);

        ItemView byId = masterDataQuery.findItem(created.getId()).orElseThrow();
        assertThat(byId.sku()).isEqualTo("RM-IT-1");
        assertThat(byId.itemType()).isEqualTo(ItemType.RAW);
        assertThat(byId.stocked()).isTrue();
        assertThat(byId.standardCost()).isEqualByComparingTo("12.5");

        ItemView bySku = masterDataQuery.findItemBySku("RM-IT-1").orElseThrow();
        assertThat(bySku.id()).isEqualTo(created.getId());
    }

    @Test
    void rejectsDuplicateSku() {
        masterDataService.createItem("RM-IT-DUP", "Dup", ItemType.RAW, "EA", true,
                BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> masterDataService.createItem("RM-IT-DUP", "Dup again", ItemType.RAW,
                "EA", true, BigDecimal.ZERO, null, null))
                .isInstanceOf(DuplicateSkuException.class);
    }
}
