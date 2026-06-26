package com.erp.sales.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Test
    void createsDraftAndAddsLines() {
        SalesOrder so = new SalesOrder("SO-1", 7L, TODAY);
        so.addLine(1L, new BigDecimal("30"), new BigDecimal("20"));

        assertThat(so.getStatus()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(so.getLines()).hasSize(1);
        assertThat(so.getLines().get(0).getLineNo()).isEqualTo(1);
    }

    @Test
    void confirmRejectsEmptyOrder() {
        SalesOrder so = new SalesOrder("SO-2", 7L, TODAY);
        assertThatThrownBy(so::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotAddLineAfterConfirm() {
        SalesOrder so = new SalesOrder("SO-3", 7L, TODAY);
        so.addLine(1L, new BigDecimal("30"), new BigDecimal("20"));
        so.confirm();

        assertThat(so.getStatus()).isEqualTo(SalesOrderStatus.CONFIRMED);
        assertThatThrownBy(() -> so.addLine(2L, new BigDecimal("5"), new BigDecimal("1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lineTracksShipmentRollup() {
        SalesOrder so = new SalesOrder("SO-4", 7L, TODAY);
        SoLine line = so.addLine(1L, new BigDecimal("10"), new BigDecimal("2"));

        line.ship(new BigDecimal("4"));
        assertThat(line.getQtyShipped()).isEqualByComparingTo("4");
        assertThat(line.outstandingToShip()).isEqualByComparingTo("6");
        assertThat(line.isFullyShipped()).isFalse();

        line.ship(new BigDecimal("6"));
        assertThat(line.isFullyShipped()).isTrue();
    }
}
