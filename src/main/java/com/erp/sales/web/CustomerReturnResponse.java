package com.erp.sales.web;

import com.erp.sales.domain.CustomerReturn;
import com.erp.sales.domain.CustomerReturnLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API view of a posted customer return (credit note) and its lines. */
public record CustomerReturnResponse(
        Long id,
        String returnNumber,
        Long salesInvoiceId,
        Long salesOrderId,
        Long partnerId,
        LocalDate postingDate,
        BigDecimal goodsAmount,
        BigDecimal vatAmount,
        BigDecimal cogsAmount,
        BigDecimal grossAmount,
        String status,
        Long creditNoteJournalEntryId,
        Instant postedAt,
        List<CustomerReturnLineView> lines) {

    public record CustomerReturnLineView(
            Long id,
            Long soLineId,
            Long itemId,
            BigDecimal qty,
            BigDecimal lineNet,
            BigDecimal lineVat,
            BigDecimal lineCogs,
            UUID movementGroupId,
            Long stockJournalEntryId) {
    }

    public static CustomerReturnResponse from(CustomerReturn ret) {
        List<CustomerReturnLineView> lines = ret.getLines().stream()
                .map(CustomerReturnResponse::toView).toList();
        return new CustomerReturnResponse(ret.getId(), ret.getReturnNumber(), ret.getSalesInvoiceId(),
                ret.getSalesOrderId(), ret.getPartnerId(), ret.getPostingDate(), ret.getGoodsAmount(),
                ret.getVatAmount(), ret.getCogsAmount(), ret.getGrossAmount(), ret.getStatus().name(),
                ret.getCreditNoteJournalEntryId(), ret.getPostedAt(), lines);
    }

    private static CustomerReturnLineView toView(CustomerReturnLine line) {
        return new CustomerReturnLineView(line.getId(), line.getSoLineId(), line.getItemId(),
                line.getQty(), line.getLineNet(), line.getLineVat(), line.getLineCogs(),
                line.getMovementGroupId(), line.getStockJournalEntryId());
    }
}
