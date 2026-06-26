package com.erp.bootstrap;

import com.erp.manufacturing.application.BomService;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.application.WorkOrderService;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.WorkOrder;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.payments.application.PaymentService;
import com.erp.payments.application.PaymentService.Allocation;
import com.erp.payments.application.PaymentService.ReceiptAllocation;
import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillService;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.purchasing.domain.VendorBill;
import com.erp.sales.application.DeliveryService;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.Delivery;
import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One-key demo seed (profile {@code seed}). Runs the full buy → make → sell slice through the real
 * posting services — so the books land balanced exactly as they would in production — rather than bypassing
 * the invariants with raw SQL. After it runs, the reconciliation health-check is healthy: GR-IR, AP,
 * Deferred-COGS, AR and WIP have all netted to zero, and inventory/COGS/revenue are booked.
 *
 * <p>This is the application composition root wiring every module's services, so it lives outside the
 * per-module boundaries (it is not part of any business module).
 */
@Component
@Profile("seed")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String ACTOR = "seed";
    private static final String VENDOR_CODE = "VEND-DEMO";

    private final MasterDataService masterDataService;
    private final MasterDataQuery masterDataQuery;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceiptService goodsReceiptService;
    private final VendorBillService vendorBillService;
    private final PaymentService paymentService;
    private final BomService bomService;
    private final WorkOrderService workOrderService;
    private final SalesOrderService salesOrderService;
    private final DeliveryService deliveryService;
    private final SalesInvoiceService salesInvoiceService;

    public DataSeeder(MasterDataService masterDataService, MasterDataQuery masterDataQuery,
                      WarehouseRepository warehouseRepository, LocationRepository locationRepository,
                      PurchaseOrderService purchaseOrderService, GoodsReceiptService goodsReceiptService,
                      VendorBillService vendorBillService, PaymentService paymentService,
                      BomService bomService, WorkOrderService workOrderService,
                      SalesOrderService salesOrderService, DeliveryService deliveryService,
                      SalesInvoiceService salesInvoiceService) {
        this.masterDataService = masterDataService;
        this.masterDataQuery = masterDataQuery;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceiptService = goodsReceiptService;
        this.vendorBillService = vendorBillService;
        this.paymentService = paymentService;
        this.bomService = bomService;
        this.workOrderService = workOrderService;
        this.salesOrderService = salesOrderService;
        this.salesInvoiceService = salesInvoiceService;
        this.deliveryService = deliveryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (masterDataQuery.findPartnerByCode(VENDOR_CODE).isPresent()) {
            log.info("Demo data already present; skipping seed.");
            return;
        }
        LocalDate today = LocalDate.now();
        Long stock = stockLocationId();
        log.info("Seeding demo buy -> make -> sell slice...");

        Long vendor = masterDataService.createPartner(VENDOR_CODE, "Demo Vendor", true, false, null, 30,
                null, null).getId();
        Long customer = masterDataService.createPartner("CUST-DEMO", "Demo Customer", false, true, null,
                30, null, null).getId();
        Long raw = masterDataService.createItem("RM-DEMO", "Demo Raw Material", ItemType.RAW, "EA", true,
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("100")).getId();
        Long finished = masterDataService.createItem("FG-DEMO", "Demo Finished Good", ItemType.FINISHED,
                "EA", true, BigDecimal.ZERO, null, null).getId();

        // BUY: PO 100 @ 10 -> receive -> bill (+5% VAT) -> pay.
        PurchaseOrder po = purchaseOrderService.createOrder(vendor,
                List.of(new PoLineInput(raw, new BigDecimal("100"), new BigDecimal("10"))), today, ACTOR);
        purchaseOrderService.confirm(po.getId(), ACTOR);
        Long poLine = po.getLines().get(0).getId();
        goodsReceiptService.receive(po.getId(), stock,
                List.of(new ReceiptLineInput(poLine, new BigDecimal("100"))), today, ACTOR);
        VendorBill bill = vendorBillService.postBill(po.getId(),
                List.of(new BillLineInput(poLine, new BigDecimal("100"), new BigDecimal("10"))),
                "STANDARD", today, ACTOR);
        paymentService.payOut(vendor, bill.getGrossAmount(), today,
                List.of(new Allocation(bill.getId(), bill.getGrossAmount())), ACTOR);

        // MAKE: single-level BOM (1 FG <- 1 RM) -> work order 50 -> release -> issue -> complete.
        BillOfMaterials bom = bomService.createBom(finished, new BigDecimal("1"),
                List.of(new ComponentInput(raw, new BigDecimal("1"), null)), ACTOR);
        WorkOrder wo = workOrderService.create(finished, bom.getId(), new BigDecimal("50"), ACTOR);
        workOrderService.release(wo.getId(), ACTOR);
        workOrderService.issue(wo.getId(), stock, today, ACTOR);
        workOrderService.complete(wo.getId(), new BigDecimal("50"), stock, today, ACTOR);

        // SELL: SO 30 @ 20 -> deliver -> invoice (+5% VAT) -> receive payment.
        SalesOrder so = salesOrderService.createOrder(customer,
                List.of(new SoLineInput(finished, new BigDecimal("30"), new BigDecimal("20"))), today,
                ACTOR);
        salesOrderService.confirm(so.getId(), ACTOR);
        Long soLine = so.getLines().get(0).getId();
        Delivery delivery = deliveryService.deliver(so.getId(), stock,
                List.of(new DeliveryLineInput(soLine, new BigDecimal("30"))), today, ACTOR);
        SalesInvoice invoice = salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLine, new BigDecimal("30"), new BigDecimal("20"))),
                "STANDARD", today, ACTOR);
        paymentService.payIn(customer, invoice.getGrossAmount(), today,
                List.of(new ReceiptAllocation(invoice.getId(), invoice.getGrossAmount())), ACTOR);

        log.info("Demo seed complete: PO {}, WO {}, delivery {}, invoice {} — books reconcile.",
                po.getPoNumber(), wo.getWoNumber(), delivery.getDeliveryNumber(),
                invoice.getInvoiceNumber());
    }

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }
}
