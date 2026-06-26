package com.erp.sales.web;

import com.erp.sales.domain.Delivery;
import com.erp.sales.domain.DeliveryLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API view of a posted delivery and its lines. */
public record DeliveryResponse(
        Long id,
        String deliveryNumber,
        Long salesOrderId,
        LocalDate postingDate,
        String status,
        Instant postedAt,
        List<DeliveryLineView> lines) {

    public record DeliveryLineView(
            Long id,
            Long soLineId,
            Long itemId,
            BigDecimal qtyShipped,
            BigDecimal unitCost,
            UUID movementGroupId,
            Long journalEntryId) {
    }

    public static DeliveryResponse from(Delivery delivery) {
        List<DeliveryLineView> lines = delivery.getLines().stream().map(DeliveryResponse::toView).toList();
        return new DeliveryResponse(delivery.getId(), delivery.getDeliveryNumber(),
                delivery.getSalesOrderId(), delivery.getPostingDate(), delivery.getStatus().name(),
                delivery.getPostedAt(), lines);
    }

    private static DeliveryLineView toView(DeliveryLine line) {
        return new DeliveryLineView(line.getId(), line.getSoLineId(), line.getItemId(),
                line.getQtyShipped(), line.getUnitCost(), line.getMovementGroupId(),
                line.getJournalEntryId());
    }
}
