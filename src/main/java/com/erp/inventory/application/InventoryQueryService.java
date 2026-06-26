package com.erp.inventory.application;

import com.erp.inventory.api.InventoryAccountBalance;
import com.erp.inventory.api.InventoryQuery;
import com.erp.inventory.api.ItemOnHand;
import com.erp.inventory.domain.ItemCostState;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Read-side implementation of the {@link InventoryQuery} port over the moving-average cost cache. */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService implements InventoryQuery {

    private final ItemCostStateRepository itemCostStateRepository;
    private final MasterDataQuery masterDataQuery;

    public InventoryQueryService(ItemCostStateRepository itemCostStateRepository,
                                 MasterDataQuery masterDataQuery) {
        this.itemCostStateRepository = itemCostStateRepository;
        this.masterDataQuery = masterDataQuery;
    }

    @Override
    public Optional<ItemOnHand> onHand(Long itemId) {
        return itemCostStateRepository.findById(itemId).map(InventoryQueryService::toView);
    }

    @Override
    public List<ItemOnHand> allOnHand() {
        return itemCostStateRepository.findAll().stream().map(InventoryQueryService::toView).toList();
    }

    @Override
    public List<InventoryAccountBalance> subledgerByAccount() {
        Map<String, BigDecimal> byAccount = new TreeMap<>();
        for (ItemCostState state : itemCostStateRepository.findAll()) {
            masterDataQuery.findItem(state.getItemId()).ifPresent(item -> {
                String account = masterDataQuery.resolveInventoryAccountCode(item.itemType());
                byAccount.merge(account, state.getTotalValue(), BigDecimal::add);
            });
        }
        return byAccount.entrySet().stream()
                .map(e -> new InventoryAccountBalance(e.getKey(), e.getValue()))
                .toList();
    }

    private static ItemOnHand toView(ItemCostState state) {
        return new ItemOnHand(state.getItemId(), state.getOnHandQty(), state.getAvgUnitCost(),
                state.getTotalValue());
    }
}
