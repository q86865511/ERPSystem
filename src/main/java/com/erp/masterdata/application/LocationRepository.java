package com.erp.masterdata.application;

import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByWarehouseIdAndLocationType(Long warehouseId, LocationType locationType);

    Optional<Location> findByWarehouseIdAndCode(Long warehouseId, String code);
}
