package com.erp.inventory.api;

import java.util.List;
import java.util.Optional;

/**
 * The inventory module's published read port for current stock positions. Other modules (e.g.
 * manufacturing's reorder report) read on-hand quantities through this contract, never the cost-state
 * entity directly.
 */
public interface InventoryQuery {

    Optional<ItemOnHand> onHand(Long itemId);

    List<ItemOnHand> allOnHand();
}
