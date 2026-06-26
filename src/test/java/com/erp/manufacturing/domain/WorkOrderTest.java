package com.erp.manufacturing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderTest {

    @Test
    void walksThroughTheStateMachine() {
        WorkOrder wo = new WorkOrder("WO-1", 1L, 9L, new BigDecimal("50"));
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);

        wo.addComponent(2L, new BigDecimal("50"));
        wo.markReleased();
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.RELEASED);

        wo.beginIssue(7L);
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(wo.getWipLocationId()).isEqualTo(7L);

        wo.getComponents().get(0).consume(new BigDecimal("50"), new BigDecimal("500"), null, null);
        wo.addComponentCost(new BigDecimal("500"));
        assertThat(wo.getTotalComponentCost()).isEqualByComparingTo("500");

        wo.markDone(new BigDecimal("50"));
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DONE);
        assertThat(wo.getQtyProduced()).isEqualByComparingTo("50");
    }

    @Test
    void releaseRejectsEmptyOrder() {
        WorkOrder wo = new WorkOrder("WO-2", 1L, 9L, new BigDecimal("50"));
        assertThatThrownBy(wo::markReleased).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotIssueBeforeRelease() {
        WorkOrder wo = new WorkOrder("WO-3", 1L, 9L, new BigDecimal("50"));
        wo.addComponent(2L, new BigDecimal("50"));
        assertThatThrownBy(() -> wo.beginIssue(7L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotAddComponentAfterRelease() {
        WorkOrder wo = new WorkOrder("WO-4", 1L, 9L, new BigDecimal("50"));
        wo.addComponent(2L, new BigDecimal("50"));
        wo.markReleased();
        assertThatThrownBy(() -> wo.addComponent(3L, new BigDecimal("1")))
                .isInstanceOf(IllegalStateException.class);
    }
}
