package com.erp.purchasing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.domain.Item;
import com.erp.masterdata.domain.Partner;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The purchasing list endpoints back the frontend's document lists; a created PO must appear. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PurchasingListIT {

    @Autowired
    private MasterDataService masterData;
    @Autowired
    private PurchaseOrderService orders;

    @Test
    void listOrdersContainsCreatedOrder() {
        Partner vendor = masterData.createPartner("PLIST-V1", "PO list vendor", true, false, null, 30,
                null, null);
        Item item = masterData.createItem("PLIST-IT1", "PO list item", ItemType.RAW, "EA", true,
                new BigDecimal("1.000000"), null, null);

        PurchaseOrder po = orders.createOrder(vendor.getId(),
                List.of(new PoLineInput(item.getId(), new BigDecimal("5"), new BigDecimal("2"))),
                LocalDate.now(), "test");

        assertThat(orders.listOrders()).extracting(PurchaseOrder::getId).contains(po.getId());
    }
}
