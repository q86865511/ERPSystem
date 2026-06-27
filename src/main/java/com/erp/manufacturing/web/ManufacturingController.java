package com.erp.manufacturing.web;

import com.erp.manufacturing.application.BomService;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.application.ReorderReport;
import com.erp.manufacturing.application.ReorderReportService;
import com.erp.manufacturing.application.WorkOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for manufacturing: bills of material and work orders. */
@RestController
@RequestMapping("/api/manufacturing")
public class ManufacturingController {

    private final BomService bomService;
    private final WorkOrderService workOrderService;
    private final ReorderReportService reorderReportService;

    public ManufacturingController(BomService bomService, WorkOrderService workOrderService,
                                   ReorderReportService reorderReportService) {
        this.bomService = bomService;
        this.workOrderService = workOrderService;
        this.reorderReportService = reorderReportService;
    }

    public record CreateBomComponent(Long componentItemId, BigDecimal qtyPer, BigDecimal scrapPct) {
    }

    public record CreateBomRequest(Long parentItemId, BigDecimal outputQty,
                                   List<CreateBomComponent> components) {
    }

    public record CreateWorkOrderRequest(Long itemId, Long bomId, BigDecimal qtyToProduce) {
    }

    public record IssueRequest(Long stockLocationId, LocalDate postingDate) {
    }

    public record CompleteRequest(BigDecimal qtyProduced, Long stockLocationId, LocalDate postingDate) {
    }

    public record CancelRequest(Long stockLocationId, LocalDate postingDate) {
    }

    @PostMapping("/boms")
    public ResponseEntity<BomResponse> createBom(@RequestBody CreateBomRequest request,
                                                 Principal principal) {
        List<ComponentInput> components = request.components().stream()
                .map(c -> new ComponentInput(c.componentItemId(), c.qtyPer(), c.scrapPct())).toList();
        BomResponse body = BomResponse.from(bomService.createBom(request.parentItemId(),
                request.outputQty(), components, actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/boms/{id}")
    public BomResponse getBom(@PathVariable Long id) {
        return BomResponse.from(bomService.getBom(id));
    }

    @GetMapping("/boms")
    public List<BomResponse> listBoms() {
        return bomService.listBoms().stream().map(BomResponse::from).toList();
    }

    @PostMapping("/work-orders")
    public ResponseEntity<WorkOrderResponse> createWorkOrder(@RequestBody CreateWorkOrderRequest request,
                                                             Principal principal) {
        WorkOrderResponse body = WorkOrderResponse.from(workOrderService.create(request.itemId(),
                request.bomId(), request.qtyToProduce(), actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/work-orders/{id}/release")
    public WorkOrderResponse release(@PathVariable Long id, Principal principal) {
        return WorkOrderResponse.from(workOrderService.release(id, actor(principal)));
    }

    @PostMapping("/work-orders/{id}/issue")
    public WorkOrderResponse issue(@PathVariable Long id, @RequestBody IssueRequest request,
                                   Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        return WorkOrderResponse.from(workOrderService.issue(id, request.stockLocationId(), postingDate,
                actor(principal)));
    }

    @PostMapping("/work-orders/{id}/complete")
    public WorkOrderResponse complete(@PathVariable Long id, @RequestBody CompleteRequest request,
                                      Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        return WorkOrderResponse.from(workOrderService.complete(id, request.qtyProduced(),
                request.stockLocationId(), postingDate, actor(principal)));
    }

    @PostMapping("/work-orders/{id}/cancel")
    public WorkOrderResponse cancel(@PathVariable Long id, @RequestBody CancelRequest request,
                                    Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        return WorkOrderResponse.from(workOrderService.cancel(id, request.stockLocationId(), postingDate,
                actor(principal)));
    }

    @GetMapping("/work-orders/{id}")
    public WorkOrderResponse getWorkOrder(@PathVariable Long id) {
        return WorkOrderResponse.from(workOrderService.getWorkOrder(id));
    }

    @GetMapping("/work-orders")
    public List<WorkOrderResponse> listWorkOrders() {
        return workOrderService.listWorkOrders().stream().map(WorkOrderResponse::from).toList();
    }

    @GetMapping("/reorder-report")
    public ReorderReport reorderReport() {
        return reorderReportService.reorderReport();
    }

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
