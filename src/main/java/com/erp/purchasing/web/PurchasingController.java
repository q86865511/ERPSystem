package com.erp.purchasing.web;

import com.erp.purchasing.application.ApAgingReport;
import com.erp.purchasing.application.ApAgingService;
import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.SupplierPerformance;
import com.erp.purchasing.application.SupplierPerformanceService;
import com.erp.purchasing.application.VendorBillService;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for purchasing: purchase orders and goods receipts. */
@RestController
@RequestMapping("/api/purchasing")
public class PurchasingController {

    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceiptService goodsReceiptService;
    private final VendorBillService vendorBillService;
    private final ApAgingService apAgingService;
    private final SupplierPerformanceService supplierPerformanceService;

    public PurchasingController(PurchaseOrderService purchaseOrderService,
                                GoodsReceiptService goodsReceiptService,
                                VendorBillService vendorBillService,
                                ApAgingService apAgingService,
                                SupplierPerformanceService supplierPerformanceService) {
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceiptService = goodsReceiptService;
        this.vendorBillService = vendorBillService;
        this.apAgingService = apAgingService;
        this.supplierPerformanceService = supplierPerformanceService;
    }

    @GetMapping("/suppliers/performance")
    public List<SupplierPerformance> supplierPerformance() {
        return supplierPerformanceService.performance();
    }

    public record CreatePoLine(@NotNull Long itemId, @NotNull BigDecimal qtyOrdered,
                               @NotNull BigDecimal unitPrice) {
    }

    public record CreatePoRequest(@NotNull Long partnerId, @NotNull LocalDate orderDate,
                                  @NotEmpty @Valid List<CreatePoLine> lines) {
    }

    public record ReceiptLine(@NotNull Long poLineId, @NotNull BigDecimal qty) {
    }

    public record CreateGrnRequest(@NotNull Long purchaseOrderId, @NotNull Long stockLocationId,
                                   LocalDate postingDate, @NotEmpty @Valid List<ReceiptLine> lines) {
    }

    public record BillLine(@NotNull Long poLineId, @NotNull BigDecimal qty, @NotNull BigDecimal unitPrice) {
    }

    public record CreateBillRequest(@NotNull Long purchaseOrderId, String taxRateCode, LocalDate postingDate,
                                    @NotEmpty @Valid List<BillLine> lines) {
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderResponse> createOrder(@Valid @RequestBody CreatePoRequest request,
                                                             Principal principal) {
        String actor = actor(principal);
        List<PoLineInput> lines = request.lines().stream()
                .map(l -> new PoLineInput(l.itemId(), l.qtyOrdered(), l.unitPrice())).toList();
        PurchaseOrderResponse body = PurchaseOrderResponse.from(purchaseOrderService.createOrder(
                request.partnerId(), lines, request.orderDate(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/purchase-orders/{id}/confirm")
    public PurchaseOrderResponse confirm(@PathVariable Long id, Principal principal) {
        return PurchaseOrderResponse.from(purchaseOrderService.confirm(id, actor(principal)));
    }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderResponse getOrder(@PathVariable Long id) {
        return PurchaseOrderResponse.from(purchaseOrderService.getOrder(id));
    }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrderResponse> listOrders() {
        return purchaseOrderService.listOrders().stream().map(PurchaseOrderResponse::from).toList();
    }

    @PostMapping("/goods-receipts")
    public ResponseEntity<GoodsReceiptResponse> receive(@Valid @RequestBody CreateGrnRequest request,
                                                        Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        List<ReceiptLineInput> lines = request.lines().stream()
                .map(l -> new ReceiptLineInput(l.poLineId(), l.qty())).toList();
        GoodsReceiptResponse body = GoodsReceiptResponse.from(goodsReceiptService.receive(
                request.purchaseOrderId(), request.stockLocationId(), lines, postingDate,
                actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/goods-receipts/{id}")
    public GoodsReceiptResponse getReceipt(@PathVariable Long id) {
        return GoodsReceiptResponse.from(goodsReceiptService.getReceipt(id));
    }

    @GetMapping("/goods-receipts")
    public List<GoodsReceiptResponse> listReceipts() {
        return goodsReceiptService.listReceipts().stream().map(GoodsReceiptResponse::from).toList();
    }

    @PostMapping("/vendor-bills")
    public ResponseEntity<VendorBillResponse> postBill(@Valid @RequestBody CreateBillRequest request,
                                                       Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        String taxRateCode = request.taxRateCode() != null ? request.taxRateCode() : "STANDARD";
        List<BillLineInput> lines = request.lines().stream()
                .map(l -> new BillLineInput(l.poLineId(), l.qty(), l.unitPrice())).toList();
        VendorBillResponse body = VendorBillResponse.from(vendorBillService.postBill(
                request.purchaseOrderId(), lines, taxRateCode, postingDate, actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/vendor-bills/{id}")
    public VendorBillResponse getBill(@PathVariable Long id) {
        return VendorBillResponse.from(vendorBillService.getBill(id));
    }

    @GetMapping("/vendor-bills")
    public List<VendorBillResponse> listBills() {
        return vendorBillService.listBills().stream().map(VendorBillResponse::from).toList();
    }

    /** Accounts-payable aging as of a date (defaults to today). */
    @GetMapping("/ap-aging")
    public ApAgingReport apAging(@RequestParam(required = false)
                                 @org.springframework.format.annotation.DateTimeFormat(iso =
                                         org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                 LocalDate asOf) {
        return apAgingService.apAging(asOf != null ? asOf : LocalDate.now());
    }

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
