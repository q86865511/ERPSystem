package com.erp.manufacturing.application;

import com.erp.manufacturing.domain.BillOfMaterials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillOfMaterialsRepository extends JpaRepository<BillOfMaterials, Long> {

    List<BillOfMaterials> findByParentItemId(Long parentItemId);
}
