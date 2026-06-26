package com.erp.purchasing.web;

import com.erp.purchasing.domain.PoLine;
import com.erp.purchasing.domain.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API view of a purchase order and its lines. */
public record PurchaseOrderResponse(
        Long id,
        String poNumber,
        Long partnerId,
        LocalDate orderDate,
        String status,
        List<PoLineView> lines) {

    public record PoLineView(
            Long id,
            int lineNo,
            Long itemId,
            BigDecimal qtyOrdered,
            BigDecimal qtyReceived,
            BigDecimal qtyBilled,
            BigDecimal unitPrice) {
    }

    public static PurchaseOrderResponse from(PurchaseOrder order) {
        List<PoLineView> lines = order.getLines().stream().map(PurchaseOrderResponse::toView).toList();
        return new PurchaseOrderResponse(order.getId(), order.getPoNumber(), order.getPartnerId(),
                order.getOrderDate(), order.getStatus().name(), lines);
    }

    private static PoLineView toView(PoLine line) {
        return new PoLineView(line.getId(), line.getLineNo(), line.getItemId(), line.getQtyOrdered(),
                line.getQtyReceived(), line.getQtyBilled(), line.getUnitPrice());
    }
}
