package com.erp.manufacturing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillOfMaterialsTest {

    @Test
    void createsActiveBomWithComponents() {
        BillOfMaterials bom = new BillOfMaterials(1L, 1, new BigDecimal("1"));
        bom.addComponent(2L, new BigDecimal("1"), null);
        bom.addComponent(3L, new BigDecimal("2"), new BigDecimal("0.05"));

        assertThat(bom.getStatus()).isEqualTo(BomStatus.ACTIVE);
        assertThat(bom.getComponents()).hasSize(2);
        assertThat(bom.getComponents().get(1).getLineNo()).isEqualTo(2);
        assertThat(bom.getComponents().get(0).getScrapPct()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsNonPositiveOutputQty() {
        assertThatThrownBy(() -> new BillOfMaterials(1L, 1, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveComponentQty() {
        BillOfMaterials bom = new BillOfMaterials(1L, 1, new BigDecimal("1"));
        assertThatThrownBy(() -> bom.addComponent(2L, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
