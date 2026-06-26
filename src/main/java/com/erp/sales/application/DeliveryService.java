package com.erp.sales.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.api.StockPosting;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.sales.domain.Delivery;
import com.erp.sales.domain.SalesOrder;
import com.erp.sales.domain.SoLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Posts deliveries against a confirmed sales order. Each shipped line drives a stock issue (the given
 * STOCK location → CUSTOMER) through {@link StockPosting} — which moves stock out at moving-average cost
 * and posts {@code Dr Deferred-COGS / Cr Finished Goods} — then bumps the SO line's shipped quantity.
 * Everything runs in one transaction; COGS is recognised later, when the customer invoice is posted.
 */
@Service
public class DeliveryService {

    private static final String SEQUENCE_SCOPE = "DELIVERY";
    private static final String SOURCE_DOC_TYPE = "DELIVERY";

    private final SalesOrderRepository salesOrderRepository;
    private final DeliveryRepository deliveryRepository;
    private final SequenceAllocator sequenceAllocator;
    private final StockPosting stockPosting;
    private final MasterDataQuery masterDataQuery;

    public DeliveryService(SalesOrderRepository salesOrderRepository,
                           DeliveryRepository deliveryRepository,
                           SequenceAllocator sequenceAllocator,
                           StockPosting stockPosting,
                           MasterDataQuery masterDataQuery) {
        this.salesOrderRepository = salesOrderRepository;
        this.deliveryRepository = deliveryRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.stockPosting = stockPosting;
        this.masterDataQuery = masterDataQuery;
    }

    public record DeliveryLineInput(Long soLineId, BigDecimal qty) {
    }

    @Transactional
    public Delivery deliver(Long soId, Long stockLocationId, List<DeliveryLineInput> lines,
                            LocalDate postingDate, String actor) {
        if (lines == null || lines.isEmpty()) {
            throw new SalesValidationException("a delivery needs at least one line");
        }
        SalesOrder order = salesOrderRepository.findById(soId)
                .orElseThrow(() -> new SalesOrderNotFoundException(soId));

        LocationView stockLocation = masterDataQuery.findLocation(stockLocationId)
                .orElseThrow(() -> new SalesValidationException(
                        "unknown location " + stockLocationId));
        if (stockLocation.type() != LocationType.STOCK) {
            throw new SalesValidationException(
                    "delivery source must be a STOCK location, was " + stockLocation.type());
        }
        LocationView customerLocation = masterDataQuery
                .findLocationByType(stockLocation.warehouseId(), LocationType.CUSTOMER)
                .orElseThrow(() -> new SalesValidationException(
                        "no CUSTOMER location in warehouse " + stockLocation.warehouseId()));

        String deliveryNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        Delivery delivery = new Delivery(deliveryNumber, soId, postingDate);

        int lineNo = 0;
        for (DeliveryLineInput input : lines) {
            lineNo++;
            SoLine soLine = order.lineById(input.soLineId());
            BigDecimal qty = input.qty();
            if (qty == null || qty.signum() <= 0) {
                throw new SalesValidationException("delivery quantity must be positive");
            }
            if (qty.compareTo(soLine.outstandingToShip()) > 0) {
                throw new SalesValidationException("over-delivery on so line " + soLine.getLineNo()
                        + ": " + qty + " exceeds outstanding " + soLine.outstandingToShip());
            }

            // A distinct sourceDocId per line keeps each leg's idempotency key unique.
            // unitCost is null: an issue leaves stock at the current moving-average cost.
            StockMovementCommand command = new StockMovementCommand(
                    soLine.getItemId(), stockLocationId, customerLocation.id(), qty, null,
                    InventoryMovementType.SHIPMENT, SOURCE_DOC_TYPE, deliveryNumber + "#" + lineNo,
                    postingDate, "delivery " + deliveryNumber);
            StockMovementResult result = stockPosting.post(command, actor);

            // unitCost is the moving average AT the issue (newAvgUnitCost is 0 after a full drain).
            delivery.addLine(input.soLineId(), soLine.getItemId(), qty, result.unitCost(),
                    result.movementGroupId(), result.journalEntryId());
            order.applyDelivery(input.soLineId(), qty);
        }

        salesOrderRepository.save(order);
        return deliveryRepository.saveAndFlush(delivery);
    }

    @Transactional(readOnly = true)
    public Delivery getDelivery(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new DeliveryNotFoundException(id));
    }
}
