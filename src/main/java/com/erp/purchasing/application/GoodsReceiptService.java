package com.erp.purchasing.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.api.StockPosting;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.purchasing.domain.GoodsReceipt;
import com.erp.purchasing.domain.PoLine;
import com.erp.purchasing.domain.PurchaseOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Posts goods receipts against a confirmed purchase order. Each received line drives a stock receipt
 * (VENDOR → the given STOCK location) through {@link StockPosting} — which moves the moving average and
 * posts {@code Dr Inventory / Cr GR-IR} — then bumps the PO line's received quantity. Everything runs in
 * one transaction.
 */
@Service
public class GoodsReceiptService {

    private static final String SEQUENCE_SCOPE = "GOODS_RECEIPT";
    private static final String SOURCE_DOC_TYPE = "GOODS_RECEIPT";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SequenceAllocator sequenceAllocator;
    private final StockPosting stockPosting;
    private final MasterDataQuery masterDataQuery;

    public GoodsReceiptService(PurchaseOrderRepository purchaseOrderRepository,
                               GoodsReceiptRepository goodsReceiptRepository,
                               SequenceAllocator sequenceAllocator,
                               StockPosting stockPosting,
                               MasterDataQuery masterDataQuery) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.stockPosting = stockPosting;
        this.masterDataQuery = masterDataQuery;
    }

    public record ReceiptLineInput(Long poLineId, BigDecimal qty) {
    }

    @Transactional
    public GoodsReceipt receive(Long poId, Long stockLocationId, List<ReceiptLineInput> lines,
                                LocalDate postingDate, String actor) {
        if (lines == null || lines.isEmpty()) {
            throw new PurchasingValidationException("a goods receipt needs at least one line");
        }
        PurchaseOrder order = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(poId));

        LocationView stockLocation = masterDataQuery.findLocation(stockLocationId)
                .orElseThrow(() -> new PurchasingValidationException(
                        "unknown location " + stockLocationId));
        if (stockLocation.type() != LocationType.STOCK) {
            throw new PurchasingValidationException(
                    "receipt target must be a STOCK location, was " + stockLocation.type());
        }
        LocationView vendorLocation = masterDataQuery
                .findLocationByType(stockLocation.warehouseId(), LocationType.VENDOR)
                .orElseThrow(() -> new PurchasingValidationException(
                        "no VENDOR location in warehouse " + stockLocation.warehouseId()));

        String grnNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        GoodsReceipt receipt = new GoodsReceipt(grnNumber, poId, postingDate);

        int lineNo = 0;
        for (ReceiptLineInput input : lines) {
            lineNo++;
            PoLine poLine = order.lineById(input.poLineId());
            BigDecimal qty = input.qty();
            if (qty == null || qty.signum() <= 0) {
                throw new PurchasingValidationException("receipt quantity must be positive");
            }
            if (qty.compareTo(poLine.outstandingToReceive()) > 0) {
                throw new PurchasingValidationException("over-receipt on po line " + poLine.getLineNo()
                        + ": " + qty + " exceeds outstanding " + poLine.outstandingToReceive());
            }

            // A distinct sourceDocId per line keeps each leg's idempotency key unique.
            StockMovementCommand command = new StockMovementCommand(
                    poLine.getItemId(), vendorLocation.id(), stockLocationId, qty, poLine.getUnitPrice(),
                    InventoryMovementType.RECEIPT, SOURCE_DOC_TYPE, grnNumber + "#" + lineNo,
                    postingDate, "goods receipt " + grnNumber);
            StockMovementResult result = stockPosting.post(command, actor);

            receipt.addLine(input.poLineId(), poLine.getItemId(), qty, poLine.getUnitPrice(),
                    result.movementGroupId(), result.journalEntryId());
            order.applyReceipt(input.poLineId(), qty);
        }

        purchaseOrderRepository.save(order);
        return goodsReceiptRepository.saveAndFlush(receipt);
    }

    @Transactional(readOnly = true)
    public GoodsReceipt getReceipt(Long id) {
        return goodsReceiptRepository.findById(id)
                .orElseThrow(() -> new GoodsReceiptNotFoundException(id));
    }
}
