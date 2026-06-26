package com.erp.purchasing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Test
    void createsDraftAndAddsLines() {
        PurchaseOrder po = new PurchaseOrder("PO-1", 7L, TODAY);
        po.addLine(1L, new BigDecimal("100"), new BigDecimal("10"));

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(po.getLines()).hasSize(1);
        assertThat(po.getLines().get(0).getLineNo()).isEqualTo(1);
    }

    @Test
    void confirmRejectsEmptyOrder() {
        PurchaseOrder po = new PurchaseOrder("PO-2", 7L, TODAY);
        assertThatThrownBy(po::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotAddLineAfterConfirm() {
        PurchaseOrder po = new PurchaseOrder("PO-3", 7L, TODAY);
        po.addLine(1L, new BigDecimal("100"), new BigDecimal("10"));
        po.confirm();

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
        assertThatThrownBy(() -> po.addLine(2L, new BigDecimal("5"), new BigDecimal("1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lineTracksReceiptRollup() {
        PurchaseOrder po = new PurchaseOrder("PO-4", 7L, TODAY);
        PoLine line = po.addLine(1L, new BigDecimal("10"), new BigDecimal("2"));

        line.receive(new BigDecimal("4"));
        assertThat(line.getQtyReceived()).isEqualByComparingTo("4");
        assertThat(line.outstandingToReceive()).isEqualByComparingTo("6");
        assertThat(line.isFullyReceived()).isFalse();

        line.receive(new BigDecimal("6"));
        assertThat(line.isFullyReceived()).isTrue();
    }
}
