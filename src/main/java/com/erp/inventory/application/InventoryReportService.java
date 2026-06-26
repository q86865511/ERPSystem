package com.erp.inventory.application;

import com.erp.inventory.domain.ItemCostState;
import com.erp.masterdata.api.ItemView;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-side inventory reporting: per-item on-hand and the subledger value rolled up to each inventory
 * control account. Reads only inventory's own {@code item_cost_state} plus the masterdata published
 * port (to map item → account), so it stays within module boundaries. The subledger-vs-GL comparison
 * is assembled from this and the ledger's trial balance.
 */
@Service
@Transactional(readOnly = true)
public class InventoryReportService {

    private final ItemCostStateRepository itemCostStateRepository;
    private final MasterDataQuery masterDataQuery;

    public InventoryReportService(ItemCostStateRepository itemCostStateRepository,
                                  MasterDataQuery masterDataQuery) {
        this.itemCostStateRepository = itemCostStateRepository;
        this.masterDataQuery = masterDataQuery;
    }

    public ItemStock onHand(Long itemId) {
        ItemView item = masterDataQuery.findItem(itemId)
                .orElseThrow(() -> new StockMovementValidationException("unknown item " + itemId));
        return itemCostStateRepository.findById(itemId)
                .map(state -> toItemStock(item.sku(), state))
                .orElseGet(() -> new ItemStock(itemId, item.sku(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public List<ItemStock> onHandAll() {
        List<ItemStock> stocks = new ArrayList<>();
        for (ItemCostState state : itemCostStateRepository.findAll()) {
            String sku = masterDataQuery.findItem(state.getItemId())
                    .map(ItemView::sku).orElse(String.valueOf(state.getItemId()));
            stocks.add(toItemStock(sku, state));
        }
        return stocks;
    }

    /** Sums each item's moving-average value into its inventory control account, ordered by code. */
    public List<AccountSubledgerValue> subledgerByInventoryAccount() {
        Map<String, BigDecimal> byAccount = new TreeMap<>();
        for (ItemCostState state : itemCostStateRepository.findAll()) {
            masterDataQuery.findItem(state.getItemId()).ifPresent(item -> {
                String account = masterDataQuery.resolveInventoryAccountCode(item.itemType());
                byAccount.merge(account, state.getTotalValue(), BigDecimal::add);
            });
        }
        Map<String, BigDecimal> ordered = new LinkedHashMap<>(byAccount);
        List<AccountSubledgerValue> result = new ArrayList<>();
        ordered.forEach((code, value) -> result.add(new AccountSubledgerValue(code, value)));
        return result;
    }

    private static ItemStock toItemStock(String sku, ItemCostState state) {
        return new ItemStock(state.getItemId(), sku, state.getOnHandQty(),
                state.getAvgUnitCost(), state.getTotalValue());
    }
}
