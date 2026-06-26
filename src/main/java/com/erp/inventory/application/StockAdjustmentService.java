package com.erp.inventory.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.domain.StockAdjustment;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Posts stock-adjustment documents. A gain (positive qty) moves stock INVENTORY_LOSS → STOCK at a given
 * or standard unit cost; a loss (negative qty) moves STOCK → INVENTORY_LOSS at the current average. The
 * document number, the two-leg movement and the balanced journal entry all commit in one transaction.
 */
@Service
public class StockAdjustmentService {

    private static final String SOURCE_DOC_TYPE = "STOCK_ADJUSTMENT";
    private static final String SEQUENCE_SCOPE = "STOCK_ADJUSTMENT";

    private final StockPostingService stockPostingService;
    private final MasterDataQuery masterDataQuery;
    private final SequenceAllocator sequenceAllocator;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    public StockAdjustmentService(StockPostingService stockPostingService,
                                  MasterDataQuery masterDataQuery,
                                  SequenceAllocator sequenceAllocator,
                                  StockAdjustmentRepository stockAdjustmentRepository) {
        this.stockPostingService = stockPostingService;
        this.masterDataQuery = masterDataQuery;
        this.sequenceAllocator = sequenceAllocator;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
    }

    @Transactional
    public StockAdjustment postAdjustment(Long itemId, Long stockLocationId, BigDecimal qtyDelta,
                                          BigDecimal unitCost, String reason, LocalDate postingDate,
                                          String actor) {
        if (qtyDelta == null || qtyDelta.signum() == 0) {
            throw new StockMovementValidationException("qtyDelta must be non-zero");
        }
        LocationView stockLocation = masterDataQuery.findLocation(stockLocationId)
                .orElseThrow(() -> new StockMovementValidationException(
                        "unknown location " + stockLocationId));
        if (stockLocation.type() != LocationType.STOCK) {
            throw new StockMovementValidationException(
                    "adjustment location must be a STOCK location, was " + stockLocation.type());
        }
        LocationView lossLocation = masterDataQuery
                .findLocationByType(stockLocation.warehouseId(), LocationType.INVENTORY_LOSS)
                .orElseThrow(() -> new StockMovementValidationException(
                        "no INVENTORY_LOSS location in warehouse " + stockLocation.warehouseId()));

        String number = sequenceAllocator.next(SEQUENCE_SCOPE);
        StockAdjustment adjustment = new StockAdjustment(
                number, itemId, stockLocationId, qtyDelta, unitCost, reason);

        boolean gain = qtyDelta.signum() > 0;
        BigDecimal qty = qtyDelta.abs();
        String memo = "stock adjustment " + number;
        StockMovementCommand command = gain
                ? new StockMovementCommand(itemId, lossLocation.id(), stockLocationId, qty, unitCost,
                        InventoryMovementType.ADJUSTMENT_IN, SOURCE_DOC_TYPE, number, postingDate, memo)
                : new StockMovementCommand(itemId, stockLocationId, lossLocation.id(), qty, null,
                        InventoryMovementType.ADJUSTMENT_OUT, SOURCE_DOC_TYPE, number, postingDate, memo);

        StockMovementResult result = stockPostingService.post(command, actor);
        adjustment.markPosted(result.movementGroupId(), result.journalEntryId(), Instant.now());
        return stockAdjustmentRepository.saveAndFlush(adjustment);
    }
}
