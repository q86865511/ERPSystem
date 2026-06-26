package com.erp.masterdata.domain;

import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.ValuationMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    @Test
    void defaultsValuationToMovingAverageAndDefaultsCostToZero() {
        Item item = new Item("RM-1", "Raw 1", ItemType.RAW, "EA", true, null, null, null);

        assertThat(item.getValuationMethod()).isEqualTo(ValuationMethod.MOVING_AVERAGE);
        assertThat(item.getStandardCost()).isEqualByComparingTo("0");
        assertThat(item.isStocked()).isTrue();
    }

    @Test
    void rejectsBlankSku() {
        assertThatThrownBy(() -> new Item(" ", "Raw", ItemType.RAW, "EA", true,
                BigDecimal.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeStandardCost() {
        assertThatThrownBy(() -> new Item("RM-2", "Raw", ItemType.RAW, "EA", true,
                new BigDecimal("-1"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
