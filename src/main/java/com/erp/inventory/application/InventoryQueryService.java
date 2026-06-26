package com.erp.inventory.application;

import com.erp.inventory.api.InventoryQuery;
import com.erp.inventory.api.ItemOnHand;
import com.erp.inventory.domain.ItemCostState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Read-side implementation of the {@link InventoryQuery} port over the moving-average cost cache. */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService implements InventoryQuery {

    private final ItemCostStateRepository itemCostStateRepository;

    public InventoryQueryService(ItemCostStateRepository itemCostStateRepository) {
        this.itemCostStateRepository = itemCostStateRepository;
    }

    @Override
    public Optional<ItemOnHand> onHand(Long itemId) {
        return itemCostStateRepository.findById(itemId).map(InventoryQueryService::toView);
    }

    @Override
    public List<ItemOnHand> allOnHand() {
        return itemCostStateRepository.findAll().stream().map(InventoryQueryService::toView).toList();
    }

    private static ItemOnHand toView(ItemCostState state) {
        return new ItemOnHand(state.getItemId(), state.getOnHandQty(), state.getAvgUnitCost(),
                state.getTotalValue());
    }
}
