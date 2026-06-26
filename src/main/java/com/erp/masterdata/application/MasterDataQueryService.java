package com.erp.masterdata.application;

import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.ItemView;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.masterdata.api.PartnerView;
import com.erp.masterdata.api.PostingRuleRole;
import com.erp.masterdata.domain.Item;
import com.erp.masterdata.domain.Location;
import com.erp.masterdata.domain.Partner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Read-side implementation of the {@link MasterDataQuery} port. Maps domain entities to published
 * views and resolves the item-type/movement-type → account-code mapping, so other modules depend only
 * on this contract.
 */
@Service
@Transactional(readOnly = true)
public class MasterDataQueryService implements MasterDataQuery {

    private final ItemRepository itemRepository;
    private final LocationRepository locationRepository;
    private final InventoryPostingRuleRepository postingRuleRepository;
    private final PartnerRepository partnerRepository;
    private final TaxRateRepository taxRateRepository;

    public MasterDataQueryService(ItemRepository itemRepository,
                                  LocationRepository locationRepository,
                                  InventoryPostingRuleRepository postingRuleRepository,
                                  PartnerRepository partnerRepository,
                                  TaxRateRepository taxRateRepository) {
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.postingRuleRepository = postingRuleRepository;
        this.partnerRepository = partnerRepository;
        this.taxRateRepository = taxRateRepository;
    }

    @Override
    public Optional<ItemView> findItem(Long id) {
        return itemRepository.findById(id).map(MasterDataQueryService::toView);
    }

    @Override
    public Optional<ItemView> findItemBySku(String sku) {
        return itemRepository.findBySku(sku).map(MasterDataQueryService::toView);
    }

    @Override
    public Optional<LocationView> findLocation(Long id) {
        return locationRepository.findById(id).map(MasterDataQueryService::toView);
    }

    @Override
    public Optional<LocationView> findLocationByType(Long warehouseId, LocationType type) {
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, type)
                .map(MasterDataQueryService::toView);
    }

    @Override
    public Optional<PartnerView> findPartner(Long id) {
        return partnerRepository.findById(id).map(MasterDataQueryService::toView);
    }

    @Override
    public Optional<PartnerView> findPartnerByCode(String code) {
        return partnerRepository.findByCode(code).map(MasterDataQueryService::toView);
    }

    @Override
    public Optional<BigDecimal> findTaxRate(String code) {
        return taxRateRepository.findById(code).map(rate -> rate.getRate());
    }

    @Override
    public String resolveInventoryAccountCode(ItemType itemType) {
        return postingRuleRepository.findByRoleAndItemType(PostingRuleRole.INVENTORY, itemType)
                .map(rule -> rule.getAccountCode())
                .orElseThrow(() -> new PostingRuleNotFoundException("INVENTORY/" + itemType));
    }

    @Override
    public String resolveCounterAccountCode(InventoryMovementType movementType) {
        return postingRuleRepository.findByRoleAndMovementType(PostingRuleRole.COUNTER, movementType)
                .map(rule -> rule.getAccountCode())
                .orElseThrow(() -> new PostingRuleNotFoundException("COUNTER/" + movementType));
    }

    private static ItemView toView(Item item) {
        return new ItemView(item.getId(), item.getSku(), item.getName(), item.getItemType(),
                item.getUom(), item.isStocked(), item.getStandardCost());
    }

    private static LocationView toView(Location location) {
        return new LocationView(location.getId(), location.getWarehouseId(), location.getCode(),
                location.getLocationType());
    }

    private static PartnerView toView(Partner partner) {
        return new PartnerView(partner.getId(), partner.getCode(), partner.getName(),
                partner.isVendor(), partner.isCustomer(), partner.getTaxId(),
                partner.getPaymentTermsDays(), partner.getApAccountCode(), partner.getArAccountCode());
    }
}
