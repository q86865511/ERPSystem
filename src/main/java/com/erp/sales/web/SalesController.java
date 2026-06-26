package com.erp.sales.web;

import com.erp.sales.application.DeliveryService;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesOrderService;
import com.erp.sales.application.SalesOrderService.SoLineInput;
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

/** REST surface for sales: sales orders and deliveries. */
@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesOrderService salesOrderService;
    private final DeliveryService deliveryService;

    public SalesController(SalesOrderService salesOrderService, DeliveryService deliveryService) {
        this.salesOrderService = salesOrderService;
        this.deliveryService = deliveryService;
    }

    public record CreateSoLine(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice) {
    }

    public record CreateSoRequest(Long partnerId, LocalDate orderDate, List<CreateSoLine> lines) {
    }

    public record DeliveryLine(Long soLineId, BigDecimal qty) {
    }

    public record CreateDeliveryRequest(Long salesOrderId, Long stockLocationId, LocalDate postingDate,
                                        List<DeliveryLine> lines) {
    }

    @PostMapping("/sales-orders")
    public ResponseEntity<SalesOrderResponse> createOrder(@RequestBody CreateSoRequest request,
                                                          Principal principal) {
        String actor = actor(principal);
        List<SoLineInput> lines = request.lines().stream()
                .map(l -> new SoLineInput(l.itemId(), l.qtyOrdered(), l.unitPrice())).toList();
        SalesOrderResponse body = SalesOrderResponse.from(salesOrderService.createOrder(
                request.partnerId(), lines, request.orderDate(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/sales-orders/{id}/confirm")
    public SalesOrderResponse confirm(@PathVariable Long id, Principal principal) {
        return SalesOrderResponse.from(salesOrderService.confirm(id, actor(principal)));
    }

    @GetMapping("/sales-orders/{id}")
    public SalesOrderResponse getOrder(@PathVariable Long id) {
        return SalesOrderResponse.from(salesOrderService.getOrder(id));
    }

    @PostMapping("/deliveries")
    public ResponseEntity<DeliveryResponse> deliver(@RequestBody CreateDeliveryRequest request,
                                                    Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        List<DeliveryLineInput> lines = request.lines().stream()
                .map(l -> new DeliveryLineInput(l.soLineId(), l.qty())).toList();
        DeliveryResponse body = DeliveryResponse.from(deliveryService.deliver(
                request.salesOrderId(), request.stockLocationId(), lines, postingDate,
                actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/deliveries/{id}")
    public DeliveryResponse getDelivery(@PathVariable Long id) {
        return DeliveryResponse.from(deliveryService.getDelivery(id));
    }

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
