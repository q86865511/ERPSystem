package com.erp.masterdata.web;

import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.application.MasterDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** REST surface for master data: items, warehouses and locations. */
@RestController
@RequestMapping("/api/masterdata")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    public record CreateItemRequest(String sku, String name, ItemType itemType, String uom,
                                    Boolean stocked, BigDecimal standardCost,
                                    BigDecimal reorderPoint, BigDecimal reorderQty) {
    }

    public record CreateWarehouseRequest(String code, String name) {
    }

    public record CreateLocationRequest(Long warehouseId, String code, LocationType locationType) {
    }

    public record CreatePartnerRequest(String code, String name, Boolean vendor, Boolean customer,
                                       String taxId, Integer paymentTermsDays, String apAccountCode,
                                       String arAccountCode) {
    }

    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@RequestBody CreateItemRequest request) {
        boolean stocked = request.stocked() == null || request.stocked();
        ItemResponse body = ItemResponse.from(masterDataService.createItem(
                request.sku(), request.name(), request.itemType(), request.uom(), stocked,
                request.standardCost(), request.reorderPoint(), request.reorderQty()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/items/{id}")
    public ItemResponse getItem(@PathVariable Long id) {
        return ItemResponse.from(masterDataService.getItem(id));
    }

    @GetMapping(value = "/items", params = "sku")
    public ItemResponse getItemBySku(@RequestParam String sku) {
        return ItemResponse.from(masterDataService.getItemBySku(sku));
    }

    @PostMapping("/warehouses")
    public ResponseEntity<WarehouseResponse> createWarehouse(@RequestBody CreateWarehouseRequest request) {
        WarehouseResponse body = WarehouseResponse.from(
                masterDataService.createWarehouse(request.code(), request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/locations")
    public ResponseEntity<LocationResponse> createLocation(@RequestBody CreateLocationRequest request) {
        LocationResponse body = LocationResponse.from(masterDataService.createLocation(
                request.warehouseId(), request.code(), request.locationType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/locations/{id}")
    public LocationResponse getLocation(@PathVariable Long id) {
        return LocationResponse.from(masterDataService.getLocation(id));
    }

    @PostMapping("/partners")
    public ResponseEntity<PartnerResponse> createPartner(@RequestBody CreatePartnerRequest request) {
        boolean vendor = request.vendor() != null && request.vendor();
        boolean customer = request.customer() != null && request.customer();
        int terms = request.paymentTermsDays() != null ? request.paymentTermsDays() : 30;
        PartnerResponse body = PartnerResponse.from(masterDataService.createPartner(
                request.code(), request.name(), vendor, customer, request.taxId(), terms,
                request.apAccountCode(), request.arAccountCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/partners/{id}")
    public PartnerResponse getPartner(@PathVariable Long id) {
        return PartnerResponse.from(masterDataService.getPartner(id));
    }
}
