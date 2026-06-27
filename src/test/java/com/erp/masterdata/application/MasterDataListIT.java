package com.erp.masterdata.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.domain.Item;
import com.erp.masterdata.domain.Location;
import com.erp.masterdata.domain.Partner;
import com.erp.masterdata.domain.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The master-data list endpoints back the frontend's list pages and selectors. Assertions use
 * "contains" with unique codes (tests commit and share the container) rather than exact counts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MasterDataListIT {

    @Autowired
    private MasterDataService service;

    @Test
    void listItemsContainsCreated() {
        Item created = service.createItem("LIST-ITEM-1", "List item", ItemType.RAW, "EA", true,
                new BigDecimal("3.000000"), null, null);
        assertThat(service.listItems()).extracting(Item::getId).contains(created.getId());
    }

    @Test
    void partnerFilterSeparatesVendorAndCustomer() {
        Partner vendor = service.createPartner("LIST-V1", "Vendor only", true, false, null, 30, null, null);
        Partner customer = service.createPartner("LIST-C1", "Customer only", false, true, null, 30, null, null);

        assertThat(service.listPartners(true, null)).extracting(Partner::getId)
                .contains(vendor.getId()).doesNotContain(customer.getId());
        assertThat(service.listPartners(null, true)).extracting(Partner::getId)
                .contains(customer.getId()).doesNotContain(vendor.getId());
        assertThat(service.listPartners(null, null)).extracting(Partner::getId)
                .contains(vendor.getId(), customer.getId());
    }

    @Test
    void listLocationsByWarehouseFiltersToThatWarehouse() {
        Warehouse wh = service.createWarehouse("LIST-WH1", "List WH");
        Location loc = service.createLocation(wh.getId(), "LIST-LOC1", LocationType.STOCK);

        List<Location> inWarehouse = service.listLocations(wh.getId());
        assertThat(inWarehouse).extracting(Location::getId).contains(loc.getId());
        assertThat(inWarehouse).allSatisfy(l -> assertThat(l.getWarehouseId()).isEqualTo(wh.getId()));
    }

    @Test
    void listWarehousesContainsCreated() {
        Warehouse wh = service.createWarehouse("LIST-WH2", "List WH 2");
        assertThat(service.listWarehouses()).extracting(Warehouse::getId).contains(wh.getId());
    }
}
