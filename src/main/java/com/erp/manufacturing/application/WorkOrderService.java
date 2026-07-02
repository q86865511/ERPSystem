package com.erp.manufacturing.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.api.StockPosting;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.BomComponent;
import com.erp.manufacturing.domain.BomStatus;
import com.erp.manufacturing.domain.WorkOrder;
import com.erp.manufacturing.domain.WorkOrderComponent;
import com.erp.manufacturing.domain.WorkOrderStatus;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

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
    private static final String WIP_ACCOUNT = "1320";
    private static final String VARIANCE_ACCOUNT = "5930";
    private static final int QTY_SCALE = 6;
    private static final int COST_SCALE = 6;

    private final WorkOrderRepository workOrderRepository;
    private final BillOfMaterialsRepository bomRepository;
    private final SequenceAllocator sequenceAllocator;
    private final StockPosting stockPosting;
    private final LedgerPosting ledgerPosting;
    private final MasterDataQuery masterDataQuery;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            BillOfMaterialsRepository bomRepository,
                            SequenceAllocator sequenceAllocator,
                            StockPosting stockPosting,
                            LedgerPosting ledgerPosting,
                            MasterDataQuery masterDataQuery) {
        this.workOrderRepository = workOrderRepository;
        this.bomRepository = bomRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.stockPosting = stockPosting;
        this.ledgerPosting = ledgerPosting;
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

    /** Creates a work order with a planned production window (for the schedule Gantt). */
    @Transactional
    public WorkOrder create(Long itemId, Long bomId, BigDecimal qtyToProduce, LocalDate plannedStart,
                            LocalDate plannedEnd, String actor) {
        WorkOrder workOrder = create(itemId, bomId, qtyToProduce, actor);
        workOrder.schedule(plannedStart, plannedEnd);
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

    /**
     * Completes the work order: receives the finished goods into stock at rolled actual cost
     * (total consumed WIP / qty produced) and sweeps any sub-unit rounding residual to Manufacturing
     * Variance so WIP nets to zero.
     */
    @Transactional
    public WorkOrder complete(Long woId, BigDecimal qtyProduced, Long fgStockLocationId,
                              LocalDate postingDate, String actor) {
        WorkOrder workOrder = getWorkOrder(woId);
        if (qtyProduced == null || qtyProduced.signum() <= 0) {
            throw new ManufacturingValidationException("qtyProduced must be positive");
        }
        LocationView stockLocation = masterDataQuery.findLocation(fgStockLocationId)
                .orElseThrow(() -> new ManufacturingValidationException(
                        "unknown location " + fgStockLocationId));
        if (stockLocation.type() != LocationType.STOCK) {
            throw new ManufacturingValidationException(
                    "completion target must be a STOCK location, was " + stockLocation.type());
        }
        if (workOrder.getWipLocationId() == null) {
            throw new ManufacturingValidationException(
                    "work order " + woId + " has no issued components to complete");
        }

        BigDecimal consumed = workOrder.getTotalComponentCost();
        BigDecimal rolledCost = consumed.divide(qtyProduced, COST_SCALE, RoundingMode.HALF_UP);

        // Receive finished goods WIP → STOCK at rolled cost (Dr Finished Goods / Cr WIP).
        StockMovementCommand command = new StockMovementCommand(
                workOrder.getItemId(), workOrder.getWipLocationId(), fgStockLocationId, qtyProduced,
                rolledCost, InventoryMovementType.MANUFACTURING_RECEIPT, SOURCE_DOC_TYPE,
                workOrder.getWoNumber() + "#receipt", postingDate,
                "work order completion " + workOrder.getWoNumber());
        StockMovementResult result = stockPosting.post(command, actor);

        // Sweep any residual (consumed − received) so the WIP control account nets to zero.
        BigDecimal residual = consumed.subtract(result.value());
        if (residual.signum() != 0) {
            List<Line> lines = residual.signum() > 0
                    ? List.of(new Line(VARIANCE_ACCOUNT, residual, null, "manufacturing variance"),
                              new Line(WIP_ACCOUNT, null, residual, "WIP clearing"))
                    : List.of(new Line(WIP_ACCOUNT, residual.negate(), null, "WIP clearing"),
                              new Line(VARIANCE_ACCOUNT, null, residual.negate(), "manufacturing variance"));
            JournalEntryRequest request = new JournalEntryRequest(null, postingDate,
                    "work order variance " + workOrder.getWoNumber(), null, SOURCE_DOC_TYPE,
                    workOrder.getWoNumber(), "VARIANCE", lines);
            ledgerPosting.post(request, actor);
        }

        workOrder.markDone(qtyProduced);
        return workOrderRepository.saveAndFlush(workOrder);
    }

    /**
     * Cancels a released/in-progress work order. Any components already issued are returned to stock
     * (MANUFACTURING_RETURN, WIP → STOCK at the issue cost, Dr component inventory / Cr WIP), so WIP nets
     * to zero. Nothing posted earlier is mutated (append-only reversal).
     */
    @Transactional
    public WorkOrder cancel(Long woId, Long stockLocationId, LocalDate postingDate, String actor) {
        WorkOrder workOrder = getWorkOrder(woId);

        if (workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS) {
            LocationView stockLocation = masterDataQuery.findLocation(stockLocationId)
                    .orElseThrow(() -> new ManufacturingValidationException(
                            "unknown location " + stockLocationId));
            if (stockLocation.type() != LocationType.STOCK) {
                throw new ManufacturingValidationException(
                        "return target must be a STOCK location, was " + stockLocation.type());
            }
            int lineNo = 0;
            for (WorkOrderComponent component : workOrder.getComponents()) {
                lineNo++;
                if (component.getConsumedQty().signum() <= 0) {
                    continue;
                }
                BigDecimal unitCost = component.getConsumedValue()
                        .divide(component.getConsumedQty(), COST_SCALE, RoundingMode.HALF_UP);
                StockMovementCommand command = new StockMovementCommand(
                        component.getComponentItemId(), workOrder.getWipLocationId(), stockLocationId,
                        component.getConsumedQty(), unitCost, InventoryMovementType.MANUFACTURING_RETURN,
                        SOURCE_DOC_TYPE, workOrder.getWoNumber() + "#return#" + lineNo, postingDate,
                        "work order cancel " + workOrder.getWoNumber());
                stockPosting.post(command, actor);
            }
        }

        workOrder.markCancelled();
        return workOrderRepository.saveAndFlush(workOrder);
    }

    @Transactional(readOnly = true)
    public WorkOrder getWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> listWorkOrders() {
        return workOrderRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "id"));
    }
}
