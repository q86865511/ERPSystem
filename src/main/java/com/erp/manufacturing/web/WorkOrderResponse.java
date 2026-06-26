package com.erp.manufacturing.web;

import com.erp.manufacturing.domain.WorkOrder;
import com.erp.manufacturing.domain.WorkOrderComponent;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** API view of a work order and its components. */
public record WorkOrderResponse(
        Long id,
        String woNumber,
        Long itemId,
        Long bomId,
        BigDecimal qtyToProduce,
        BigDecimal qtyProduced,
        Long wipLocationId,
        BigDecimal totalComponentCost,
        String status,
        List<WorkOrderComponentView> components) {

    public record WorkOrderComponentView(
            Long id,
            int lineNo,
            Long componentItemId,
            BigDecimal plannedQty,
            BigDecimal consumedQty,
            BigDecimal consumedValue,
            UUID movementGroupId,
            Long journalEntryId) {
    }

    public static WorkOrderResponse from(WorkOrder wo) {
        List<WorkOrderComponentView> components = wo.getComponents().stream()
                .map(WorkOrderResponse::toView).toList();
        return new WorkOrderResponse(wo.getId(), wo.getWoNumber(), wo.getItemId(), wo.getBomId(),
                wo.getQtyToProduce(), wo.getQtyProduced(), wo.getWipLocationId(),
                wo.getTotalComponentCost(), wo.getStatus().name(), components);
    }

    private static WorkOrderComponentView toView(WorkOrderComponent component) {
        return new WorkOrderComponentView(component.getId(), component.getLineNo(),
                component.getComponentItemId(), component.getPlannedQty(), component.getConsumedQty(),
                component.getConsumedValue(), component.getMovementGroupId(),
                component.getJournalEntryId());
    }
}
