package com.erp.sales.web;

import com.erp.sales.domain.SalesOrder;
import com.erp.sales.domain.SoLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API view of a sales order and its lines. */
public record SalesOrderResponse(
        Long id,
        String soNumber,
        Long partnerId,
        LocalDate orderDate,
        String status,
        List<SoLineView> lines) {

    public record SoLineView(
            Long id,
            int lineNo,
            Long itemId,
            BigDecimal qtyOrdered,
            BigDecimal qtyShipped,
            BigDecimal qtyInvoiced,
            BigDecimal unitPrice) {
    }

    public static SalesOrderResponse from(SalesOrder order) {
        List<SoLineView> lines = order.getLines().stream().map(SalesOrderResponse::toView).toList();
        return new SalesOrderResponse(order.getId(), order.getSoNumber(), order.getPartnerId(),
                order.getOrderDate(), order.getStatus().name(), lines);
    }

    private static SoLineView toView(SoLine line) {
        return new SoLineView(line.getId(), line.getLineNo(), line.getItemId(), line.getQtyOrdered(),
                line.getQtyShipped(), line.getQtyInvoiced(), line.getUnitPrice());
    }
}
