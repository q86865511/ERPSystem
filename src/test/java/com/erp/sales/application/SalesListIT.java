package com.erp.sales.application;

import com.erp.TestcontainersConfiguration;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.domain.Item;
import com.erp.masterdata.domain.Partner;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.SalesOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The sales list endpoints back the frontend's document lists; a created SO must appear. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SalesListIT {

    @Autowired
    private MasterDataService masterData;
    @Autowired
    private SalesOrderService orders;

    @Test
    void listOrdersContainsCreatedOrder() {
        Partner customer = masterData.createPartner("SLIST-C1", "SO list customer", false, true, null, 30,
                null, null);
        Item item = masterData.createItem("SLIST-IT1", "SO list item", ItemType.FINISHED, "EA", true,
                new BigDecimal("1.000000"), null, null);

        SalesOrder so = orders.createOrder(customer.getId(),
                List.of(new SoLineInput(item.getId(), new BigDecimal("4"), new BigDecimal("9"))),
                LocalDate.now(), "test");

        assertThat(orders.listOrders()).extracting(SalesOrder::getId).contains(so.getId());
    }
}
