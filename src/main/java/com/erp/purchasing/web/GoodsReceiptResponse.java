package com.erp.purchasing.web;

import com.erp.purchasing.domain.GoodsReceipt;
import com.erp.purchasing.domain.GrnLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API view of a posted goods receipt and its lines. */
public record GoodsReceiptResponse(
        Long id,
        String grnNumber,
        Long purchaseOrderId,
        LocalDate postingDate,
        String status,
        Instant postedAt,
        List<GrnLineView> lines) {

    public record GrnLineView(
            Long id,
            Long poLineId,
            Long itemId,
            BigDecimal qtyReceived,
            BigDecimal unitCost,
            UUID movementGroupId,
            Long journalEntryId) {
    }

    public static GoodsReceiptResponse from(GoodsReceipt receipt) {
        List<GrnLineView> lines = receipt.getLines().stream().map(GoodsReceiptResponse::toView).toList();
        return new GoodsReceiptResponse(receipt.getId(), receipt.getGrnNumber(),
                receipt.getPurchaseOrderId(), receipt.getPostingDate(), receipt.getStatus().name(),
                receipt.getPostedAt(), lines);
    }

    private static GrnLineView toView(GrnLine line) {
        return new GrnLineView(line.getId(), line.getPoLineId(), line.getItemId(),
                line.getQtyReceived(), line.getUnitCost(), line.getMovementGroupId(),
                line.getJournalEntryId());
    }
}
