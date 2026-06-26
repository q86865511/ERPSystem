package com.erp.masterdata.web;

import com.erp.masterdata.domain.Warehouse;

/** API view of a warehouse. */
public record WarehouseResponse(Long id, String code, String name, boolean active) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(), warehouse.getCode(), warehouse.getName(), warehouse.isActive());
    }
}
