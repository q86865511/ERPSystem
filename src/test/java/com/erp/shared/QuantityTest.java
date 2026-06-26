package com.erp.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuantityTest {

    @Test
    void normalisesToScaleSix() {
        assertThat(Quantity.of("10").amount()).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    @Test
    void addsAndSubtractsExactly() {
        Quantity sum = Quantity.of("0.100000").add(Quantity.of("0.200000"));
        assertThat(sum.amount()).isEqualByComparingTo("0.300000");

        Quantity diff = Quantity.of("1").subtract(Quantity.of("0.300000"));
        assertThat(diff.amount()).isEqualByComparingTo("0.700000");
    }

    @Test
    void multiplyCostRoundsToMoneyScale() {
        // 3 × 3.333333 = 9.999999 → money scale 4 HALF_UP = 10.0000
        assertThat(Quantity.of("3").multiplyCost(new BigDecimal("3.333333")))
                .isEqualByComparingTo("10.0000");
    }

    @Test
    void zeroAndSignHelpers() {
        assertThat(Quantity.zero().isZero()).isTrue();
        assertThat(Quantity.of("5").isPositive()).isTrue();
        assertThat(Quantity.of("-5").isNegative()).isTrue();
    }

    @Test
    void isGreaterThanComparesAmounts() {
        assertThat(Quantity.of("5").isGreaterThan(Quantity.of("4.999999"))).isTrue();
        assertThat(Quantity.of("5").isGreaterThan(Quantity.of("5"))).isFalse();
    }

    @Test
    void equalityIgnoresTrailingScaleDifferences() {
        assertThat(Quantity.of("10")).isEqualTo(Quantity.of("10.000000"));
    }
}
