package com.erp.sales.web;

import com.erp.sales.application.ArAgingReport;
import com.erp.sales.application.ArAgingService;
import com.erp.sales.application.CustomerReturnService;
import com.erp.sales.application.DeliveryService;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SalesInvoiceService salesInvoiceService;
    private final ArAgingService arAgingService;
    private final CustomerReturnService customerReturnService;

    public SalesController(SalesOrderService salesOrderService, DeliveryService deliveryService,
                           SalesInvoiceService salesInvoiceService, ArAgingService arAgingService,
                           CustomerReturnService customerReturnService) {
        this.salesOrderService = salesOrderService;
        this.deliveryService = deliveryService;
        this.salesInvoiceService = salesInvoiceService;
        this.arAgingService = arAgingService;
        this.customerReturnService = customerReturnService;
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

    public record InvoiceLine(Long soLineId, BigDecimal qty, BigDecimal unitPrice) {
    }

    public record CreateInvoiceRequest(Long salesOrderId, String taxRateCode, LocalDate postingDate,
                                       List<InvoiceLine> lines) {
    }

    public record CreateReturnRequest(Long salesInvoiceId, Long stockLocationId, LocalDate postingDate) {
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

    @PostMapping("/sales-invoices")
    public ResponseEntity<SalesInvoiceResponse> postInvoice(@RequestBody CreateInvoiceRequest request,
                                                            Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        String taxRateCode = request.taxRateCode() != null ? request.taxRateCode() : "STANDARD";
        List<InvoiceLineInput> lines = request.lines().stream()
                .map(l -> new InvoiceLineInput(l.soLineId(), l.qty(), l.unitPrice())).toList();
        SalesInvoiceResponse body = SalesInvoiceResponse.from(salesInvoiceService.postInvoice(
                request.salesOrderId(), lines, taxRateCode, postingDate, actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/sales-invoices/{id}")
    public SalesInvoiceResponse getInvoice(@PathVariable Long id) {
        return SalesInvoiceResponse.from(salesInvoiceService.getInvoice(id));
    }

    @PostMapping("/customer-returns")
    public ResponseEntity<CustomerReturnResponse> postReturn(@RequestBody CreateReturnRequest request,
                                                             Principal principal) {
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : LocalDate.now();
        CustomerReturnResponse body = CustomerReturnResponse.from(customerReturnService.postReturn(
                request.salesInvoiceId(), request.stockLocationId(), postingDate, actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/customer-returns/{id}")
    public CustomerReturnResponse getReturn(@PathVariable Long id) {
        return CustomerReturnResponse.from(customerReturnService.getReturn(id));
    }

    /** Accounts-receivable aging as of a date (defaults to today). */
    @GetMapping("/ar-aging")
    public ArAgingReport arAging(@RequestParam(required = false)
                                 @org.springframework.format.annotation.DateTimeFormat(iso =
                                         org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                 LocalDate asOf) {
        return arAgingService.arAging(asOf != null ? asOf : LocalDate.now());
    }

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
