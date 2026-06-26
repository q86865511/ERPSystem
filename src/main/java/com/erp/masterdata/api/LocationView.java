package com.erp.masterdata.api;

/** Published read view of a location: its id, owning warehouse and type. */
public record LocationView(
        Long id,
        Long warehouseId,
        String code,
        LocationType type) {
}
