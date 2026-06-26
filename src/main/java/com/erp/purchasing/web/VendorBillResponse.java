package com.erp.purchasing.web;

import com.erp.purchasing.domain.BillLine;
import com.erp.purchasing.domain.VendorBill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API view of a vendor bill and its lines. */
public record VendorBillResponse(
        Long id,
        String billNumber,
        Long purchaseOrderId,
        Long partnerId,
        LocalDate postingDate,
        BigDecimal goodsAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        BigDecimal amountPaid,
        BigDecimal openBalance,
        String status,
        Long journalEntryId,
        Instant postedAt,
        List<BillLineView> lines) {

    public record BillLineView(
            Long id,
            Long poLineId,
            Long itemId,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal lineNet,
            BigDecimal lineVat,
            String matchStatus) {
    }

    public static VendorBillResponse from(VendorBill bill) {
        List<BillLineView> lines = bill.getLines().stream().map(VendorBillResponse::toView).toList();
        return new VendorBillResponse(bill.getId(), bill.getBillNumber(), bill.getPurchaseOrderId(),
                bill.getPartnerId(), bill.getPostingDate(), bill.getGoodsAmount(), bill.getVatAmount(),
                bill.getGrossAmount(), bill.getAmountPaid(), bill.openBalance(), bill.getStatus().name(),
                bill.getJournalEntryId(), bill.getPostedAt(), lines);
    }

    private static BillLineView toView(BillLine line) {
        return new BillLineView(line.getId(), line.getPoLineId(), line.getItemId(), line.getQty(),
                line.getUnitPrice(), line.getLineNet(), line.getLineVat(), line.getMatchStatus().name());
    }
}
