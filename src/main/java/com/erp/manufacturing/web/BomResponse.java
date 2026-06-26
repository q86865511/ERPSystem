package com.erp.manufacturing.web;

import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.BomComponent;

import java.math.BigDecimal;
import java.util.List;

/** API view of a bill of materials and its components. */
public record BomResponse(
        Long id,
        Long parentItemId,
        int version,
        BigDecimal outputQty,
        String status,
        List<BomComponentView> components) {

    public record BomComponentView(
            Long id,
            int lineNo,
            Long componentItemId,
            BigDecimal qtyPer,
            BigDecimal scrapPct) {
    }

    public static BomResponse from(BillOfMaterials bom) {
        List<BomComponentView> components = bom.getComponents().stream().map(BomResponse::toView).toList();
        return new BomResponse(bom.getId(), bom.getParentItemId(), bom.getVersion(), bom.getOutputQty(),
                bom.getStatus().name(), components);
    }

    private static BomComponentView toView(BomComponent component) {
        return new BomComponentView(component.getId(), component.getLineNo(),
                component.getComponentItemId(), component.getQtyPer(), component.getScrapPct());
    }
}
