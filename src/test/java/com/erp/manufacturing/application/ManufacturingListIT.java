package com.erp.manufacturing.application;

import com.erp.TestcontainersConfiguration;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.domain.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The manufacturing list endpoints back the frontend's BOM/work-order lists; a created BOM must appear. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ManufacturingListIT {

    @Autowired
    private MasterDataService masterData;
    @Autowired
    private BomService boms;

    @Test
    void listBomsContainsCreatedBom() {
        Item parent = masterData.createItem("MLIST-FG1", "Mfg list FG", ItemType.FINISHED, "EA", true,
                new BigDecimal("1.000000"), null, null);
        Item component = masterData.createItem("MLIST-RM1", "Mfg list RM", ItemType.RAW, "EA", true,
                new BigDecimal("1.000000"), null, null);

        BillOfMaterials bom = boms.createBom(parent.getId(), new BigDecimal("1"),
                List.of(new ComponentInput(component.getId(), new BigDecimal("2"), BigDecimal.ZERO)),
                "test");

        assertThat(boms.listBoms()).extracting(BillOfMaterials::getId).contains(bom.getId());
    }
}
