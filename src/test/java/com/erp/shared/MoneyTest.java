package com.erp.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalisesToScaleFour() {
        assertThat(Money.of("10", "TWD").amount()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void addsAndSubtractsExactly() {
        Money sum = Money.of("0.10", "TWD").add(Money.of("0.20", "TWD"));
        assertThat(sum.amount()).isEqualByComparingTo("0.30");

        Money diff = Money.of("1.00", "TWD").subtract(Money.of("0.30", "TWD"));
        assertThat(diff.amount()).isEqualByComparingTo("0.70");
    }

    @Test
    void zeroAndSignHelpers() {
        assertThat(Money.zero("TWD").isZero()).isTrue();
        assertThat(Money.of("5", "TWD").isPositive()).isTrue();
        assertThat(Money.of("-5", "TWD").isNegative()).isTrue();
    }

    @Test
    void rejectsMixingCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "TWD").add(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }

    @Test
    void equalityIgnoresTrailingScaleDifferences() {
        assertThat(Money.of("10", "TWD")).isEqualTo(Money.of("10.0000", "TWD"));
    }
}
