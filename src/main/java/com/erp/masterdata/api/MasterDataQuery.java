package com.erp.masterdata.api;

import java.util.Optional;

/**
 * The masterdata module's published read port. Other modules resolve items, locations and the
 * item-type/movement-type → ledger-account mapping through this interface only — they never import
 * masterdata's domain entities or repositories, and they reference ledger accounts by <em>code</em>
 * (a value), never by account id.
 */
public interface MasterDataQuery {

    Optional<ItemView> findItem(Long id);

    Optional<ItemView> findItemBySku(String sku);

    Optional<LocationView> findLocation(Long id);

    /** Finds the (single) location of a given type within a warehouse, e.g. its INVENTORY_LOSS sink. */
    Optional<LocationView> findLocationByType(Long warehouseId, LocationType type);

    /**
     * The inventory control account code for an item type (RAW→1310, WIP→1320, FINISHED→1330).
     *
     * @throws RuntimeException a {@code PostingRuleNotFoundException} if no rule is configured.
     */
    String resolveInventoryAccountCode(ItemType itemType);

    /**
     * The counter (offset) account code for a movement type, e.g. ADJUSTMENT_IN/OUT → 6000.
     *
     * @throws RuntimeException a {@code PostingRuleNotFoundException} if no rule is configured.
     */
    String resolveCounterAccountCode(InventoryMovementType movementType);
}
