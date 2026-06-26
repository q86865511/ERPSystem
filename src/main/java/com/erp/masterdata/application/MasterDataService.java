package com.erp.masterdata.application;

import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.domain.Item;
import com.erp.masterdata.domain.Location;
import com.erp.masterdata.domain.Partner;
import com.erp.masterdata.domain.Warehouse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Write-side master-data operations: create items, warehouses and locations. */
@Service
public class MasterDataService {

    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final PartnerRepository partnerRepository;

    public MasterDataService(ItemRepository itemRepository,
                             WarehouseRepository warehouseRepository,
                             LocationRepository locationRepository,
                             PartnerRepository partnerRepository) {
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.partnerRepository = partnerRepository;
    }

    @Transactional
    public Item createItem(String sku, String name, ItemType itemType, String uom, boolean stocked,
                           BigDecimal standardCost, BigDecimal reorderPoint, BigDecimal reorderQty) {
        if (itemRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        Item item = new Item(sku, name, itemType, uom, stocked, standardCost, reorderPoint, reorderQty);
        return itemRepository.saveAndFlush(item);
    }

    @Transactional
    public Warehouse createWarehouse(String code, String name) {
        return warehouseRepository.saveAndFlush(new Warehouse(code, name));
    }

    @Transactional
    public Location createLocation(Long warehouseId, String code, LocationType locationType) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new WarehouseNotFoundException(warehouseId);
        }
        return locationRepository.saveAndFlush(new Location(warehouseId, code, locationType));
    }

    @Transactional(readOnly = true)
    public Item getItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("with id " + id));
    }

    @Transactional(readOnly = true)
    public Item getItemBySku(String sku) {
        return itemRepository.findBySku(sku)
                .orElseThrow(() -> new ItemNotFoundException("with sku " + sku));
    }

    @Transactional(readOnly = true)
    public Location getLocation(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));
    }

    @Transactional
    public Partner createPartner(String code, String name, boolean vendor, boolean customer,
                                 String taxId, int paymentTermsDays, String apAccountCode,
                                 String arAccountCode) {
        if (partnerRepository.existsByCode(code)) {
            throw new DuplicatePartnerCodeException(code);
        }
        Partner partner = new Partner(code, name, vendor, customer, taxId, paymentTermsDays,
                apAccountCode, arAccountCode);
        return partnerRepository.saveAndFlush(partner);
    }

    @Transactional(readOnly = true)
    public Partner getPartner(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new PartnerNotFoundException("with id " + id));
    }
}
