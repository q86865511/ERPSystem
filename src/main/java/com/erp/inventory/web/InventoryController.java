package com.erp.inventory.web;

import com.erp.inventory.application.AccountSubledgerValue;
import com.erp.inventory.application.InventoryReportService;
import com.erp.inventory.application.ItemStatus;
import com.erp.inventory.application.ItemStock;
import com.erp.inventory.application.StockAdjustmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for inventory: post stock adjustments and read on-hand / reconciliation figures. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final StockAdjustmentService stockAdjustmentService;
    private final InventoryReportService inventoryReportService;

    public InventoryController(StockAdjustmentService stockAdjustmentService,
                              InventoryReportService inventoryReportService) {
        this.stockAdjustmentService = stockAdjustmentService;
        this.inventoryReportService = inventoryReportService;
    }

    // unitCost / reason are optional at the request layer; the domain still enforces reason (→ 400).
    public record CreateAdjustmentRequest(@NotNull Long itemId, @NotNull Long locationId,
                                          @NotNull BigDecimal qtyDelta, BigDecimal unitCost,
                                          String reason, LocalDate postingDate) {
    }

    @PostMapping("/adjustments")
    public ResponseEntity<AdjustmentResponse> postAdjustment(@Valid @RequestBody CreateAdjustmentRequest request,
                                                             Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        AdjustmentResponse body = AdjustmentResponse.from(stockAdjustmentService.postAdjustment(
                request.itemId(), request.locationId(), request.qtyDelta(), request.unitCost(),
                request.reason(), postingDate, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/items/{itemId}/on-hand")
    public ItemStock onHand(@PathVariable Long itemId) {
        return inventoryReportService.onHand(itemId);
    }

    /** Inventory subledger value rolled up to each control account — the inventory side of the
     *  reconciliation health-check (compared against the GL trial balance). */
    @GetMapping("/reconciliation")
    public List<AccountSubledgerValue> reconciliation() {
        return inventoryReportService.subledgerByInventoryAccount();
    }

    /** Per-item on-hand value + OUT/LOW/OK flag (highest value first) for the inventory heat treemap. */
    @GetMapping("/items-status")
    public List<ItemStatus> itemsStatus() {
        return inventoryReportService.itemsStatus();
    }
}
