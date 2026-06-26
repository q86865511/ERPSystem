package com.erp.purchasing.web;

import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
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

/** REST surface for purchasing: purchase orders and goods receipts. */
@RestController
@RequestMapping("/api/purchasing")
public class PurchasingController {

    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceiptService goodsReceiptService;

    public PurchasingController(PurchaseOrderService purchaseOrderService,
                                GoodsReceiptService goodsReceiptService) {
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceiptService = goodsReceiptService;
    }

    public record CreatePoLine(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice) {
    }

    public record CreatePoRequest(Long partnerId, LocalDate orderDate, List<CreatePoLine> lines) {
    }

    public record ReceiptLine(Long poLineId, BigDecimal qty) {
    }

    public record CreateGrnRequest(Long purchaseOrderId, Long stockLocationId, LocalDate postingDate,
                                   List<ReceiptLine> lines) {
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderResponse> createOrder(@RequestBody CreatePoRequest request,
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

    @PostMapping("/goods-receipts")
    public ResponseEntity<GoodsReceiptResponse> receive(@RequestBody CreateGrnRequest request,
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

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
