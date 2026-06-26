package com.erp.masterdata.web;

import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.domain.Location;

/** API view of a location. */
public record LocationResponse(Long id, Long warehouseId, String code, LocationType locationType,
                               boolean active) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(), location.getWarehouseId(), location.getCode(),
                location.getLocationType(), location.isActive());
    }
}
