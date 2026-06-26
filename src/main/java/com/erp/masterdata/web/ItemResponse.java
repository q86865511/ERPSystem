package com.erp.masterdata.web;

import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.domain.Item;

import java.math.BigDecimal;

/** API view of an item. */
public record ItemResponse(
        Long id,
        String sku,
        String name,
        ItemType itemType,
        String uom,
        boolean stocked,
        BigDecimal standardCost,
        BigDecimal reorderPoint,
        BigDecimal reorderQty) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getSku(),
                item.getName(),
                item.getItemType(),
                item.getUom(),
                item.isStocked(),
                item.getStandardCost(),
                item.getReorderPoint(),
                item.getReorderQty());
    }
}
