package com.erp.manufacturing.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.api.StockPosting;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.BomComponent;
import com.erp.manufacturing.domain.BomStatus;
import com.erp.manufacturing.domain.WorkOrder;
import com.erp.manufacturing.domain.WorkOrderComponent;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Creates, releases and issues work orders. Release snapshots the BOM into frozen planned components;
 * issue consumes them into WIP through {@link StockPosting} (MANUFACTURING_ISSUE, the given STOCK
 * location → PRODUCTION_WIP), which posts {@code Dr WIP / Cr component inventory} at moving-average cost,
 * and accumulates the consumed cost for the completion roll-up. Each step runs in one transaction.
 */
@Service
public class WorkOrderService {

    private static final String SEQUENCE_SCOPE = "WORK_ORDER";
    private static final String SOURCE_DOC_TYPE = "WORK_ORDER";
    private static final int QTY_SCALE = 6;

    private final WorkOrderRepository workOrderRepository;
    private final BillOfMaterialsRepository bomRepository;
    private final SequenceAllocator sequenceAllocator;
    private final StockPosting stockPosting;
    private final MasterDataQuery masterDataQuery;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            BillOfMaterialsRepository bomRepository,
                            SequenceAllocator sequenceAllocator,
                            StockPosting stockPosting,
                            MasterDataQuery masterDataQuery) {
        this.workOrderRepository = workOrderRepository;
        this.bomRepository = bomRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.stockPosting = stockPosting;
        this.masterDataQuery = masterDataQuery;
    }

    @Transactional
    public WorkOrder create(Long itemId, Long bomId, BigDecimal qtyToProduce, String actor) {
        if (masterDataQuery.findItem(itemId).isEmpty()) {
            throw new ManufacturingValidationException("unknown item " + itemId);
        }
        BillOfMaterials bom = bomRepository.findById(bomId)
                .orElseThrow(() -> new BomNotFoundException(bomId));
        if (bom.getStatus() != BomStatus.ACTIVE) {
            throw new ManufacturingValidationException("BOM " + bomId + " is not ACTIVE");
        }
        if (!bom.getParentItemId().equals(itemId)) {
            throw new ManufacturingValidationException(
                    "BOM " + bomId + " does not produce item " + itemId);
        }
        String woNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        WorkOrder workOrder = new WorkOrder(woNumber, itemId, bomId, qtyToProduce);
        return workOrderRepository.saveAndFlush(workOrder);
    }

    /** Snapshots the BOM (scaled to the order quantity) into frozen planned components. */
    @Transactional
    public WorkOrder release(Long woId, String actor) {
        WorkOrder workOrder = getWorkOrder(woId);
        BillOfMaterials bom = bomRepository.findById(workOrder.getBomId())
                .orElseThrow(() -> new BomNotFoundException(workOrder.getBomId()));
        for (BomComponent component : bom.getComponents()) {
            BigDecimal plannedQty = component.getQtyPer()
                    .multiply(workOrder.getQtyToProduce())
                    .divide(bom.getOutputQty(), QTY_SCALE, RoundingMode.HALF_UP);
            workOrder.addComponent(component.getComponentItemId(), plannedQty);
        }
        workOrder.markReleased();
        return workOrderRepository.saveAndFlush(workOrder);
    }

    /** Issues all planned components into WIP (STOCK → PRODUCTION_WIP). */
    @Transactional
    public WorkOrder issue(Long woId, Long stockLocationId, LocalDate postingDate, String actor) {
        WorkOrder workOrder = getWorkOrder(woId);

        LocationView stockLocation = masterDataQuery.findLocation(stockLocationId)
                .orElseThrow(() -> new ManufacturingValidationException(
                        "unknown location " + stockLocationId));
        if (stockLocation.type() != LocationType.STOCK) {
            throw new ManufacturingValidationException(
                    "issue source must be a STOCK location, was " + stockLocation.type());
        }
        LocationView wipLocation = masterDataQuery
                .findLocationByType(stockLocation.warehouseId(), LocationType.PRODUCTION_WIP)
                .orElseThrow(() -> new ManufacturingValidationException(
                        "no PRODUCTION_WIP location in warehouse " + stockLocation.warehouseId()));

        workOrder.beginIssue(wipLocation.id());

        int lineNo = 0;
        for (WorkOrderComponent component : workOrder.getComponents()) {
            lineNo++;
            StockMovementCommand command = new StockMovementCommand(
                    component.getComponentItemId(), stockLocationId, wipLocation.id(),
                    component.getPlannedQty(), null, InventoryMovementType.MANUFACTURING_ISSUE,
                    SOURCE_DOC_TYPE, workOrder.getWoNumber() + "#issue#" + lineNo, postingDate,
                    "work order issue " + workOrder.getWoNumber());
            StockMovementResult result = stockPosting.post(command, actor);
            component.consume(component.getPlannedQty(), result.value(), result.movementGroupId(),
                    result.journalEntryId());
            workOrder.addComponentCost(result.value());
        }
        return workOrderRepository.saveAndFlush(workOrder);
    }

    @Transactional(readOnly = true)
    public WorkOrder getWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }
}
