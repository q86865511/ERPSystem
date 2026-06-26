package com.erp.inventory.domain;

import com.erp.shared.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the moving weighted-average cost logic — the heart of inventory valuation. */
class ItemCostStateTest {

    @Test
    void weightedAverageAcrossReceipts() {
        ItemCostState cost = new ItemCostState(1L);

        BigDecimal firstValue = cost.applyReceipt(Quantity.of("100"), new BigDecimal("10"));
        assertThat(firstValue).isEqualByComparingTo("1000.0000");
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("100");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");

        BigDecimal secondValue = cost.applyReceipt(Quantity.of("100"), new BigDecimal("12"));
        assertThat(secondValue).isEqualByComparingTo("1200.0000");
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("200");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("2200");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("11");
    }

    @Test
    void issueLeavesAverageUnchangedAndReducesValue() {
        ItemCostState cost = new ItemCostState(1L);
        cost.applyReceipt(Quantity.of("100"), new BigDecimal("10"));

        BigDecimal valueOut = cost.applyIssue(Quantity.of("40"));
        assertThat(valueOut).isEqualByComparingTo("400.0000");
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("60");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("600");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("10");
    }

    @Test
    void fullDrainZeroesValueAndAverage() {
        ItemCostState cost = new ItemCostState(1L);
        cost.applyReceipt(Quantity.of("100"), new BigDecimal("10"));

        BigDecimal valueOut = cost.applyIssue(Quantity.of("100"));
        assertThat(valueOut).isEqualByComparingTo("1000");
        assertThat(cost.getOnHandQty()).isEqualByComparingTo("0");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("0");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("0");
    }

    @Test
    void issueBeyondOnHandThrows() {
        ItemCostState cost = new ItemCostState(1L);
        cost.applyReceipt(Quantity.of("100"), new BigDecimal("10"));

        assertThat(cost.wouldGoNegative(Quantity.of("101"))).isTrue();
        assertThatThrownBy(() -> cost.applyIssue(Quantity.of("101")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void averageIsHeldToScaleSix() {
        ItemCostState cost = new ItemCostState(1L);

        // value 10 over 3 units → average 3.333333 at scale 6.
        cost.applyReceipt(Quantity.of("3"), new BigDecimal("3.333333"));
        assertThat(cost.getTotalValue()).isEqualByComparingTo("10.0000");
        assertThat(cost.getAvgUnitCost()).isEqualByComparingTo("3.333333");
    }
}
