package com.erp.manufacturing.application;

import com.erp.inventory.api.InventoryQuery;
import com.erp.inventory.api.ItemOnHand;
import com.erp.manufacturing.application.ReorderReport.ReorderItem;
import com.erp.masterdata.api.ItemView;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-side reorder-point report. Joins each item's on-hand quantity (inventory port) with its reorder
 * point (master data) and lists those at or below the point, with the suggested reorder quantity.
 */
@Service
@Transactional(readOnly = true)
public class ReorderReportService {

    private final InventoryQuery inventoryQuery;
    private final MasterDataQuery masterDataQuery;

    public ReorderReportService(InventoryQuery inventoryQuery, MasterDataQuery masterDataQuery) {
        this.inventoryQuery = inventoryQuery;
        this.masterDataQuery = masterDataQuery;
    }

    public ReorderReport reorderReport() {
        List<ReorderItem> items = new ArrayList<>();
        for (ItemOnHand stock : inventoryQuery.allOnHand()) {
            ItemView item = masterDataQuery.findItem(stock.itemId()).orElse(null);
            if (item == null || item.reorderPoint() == null) {
                continue;
            }
            if (stock.onHandQty().compareTo(item.reorderPoint()) <= 0) {
                items.add(new ReorderItem(item.id(), item.sku(), item.name(), stock.onHandQty(),
                        item.reorderPoint(), item.reorderQty()));
            }
        }
        return new ReorderReport(items);
    }
}
