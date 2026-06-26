package com.erp.manufacturing.application;

import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.masterdata.api.MasterDataQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Defines bills of material. A BOM is master data and posts nothing. */
@Service
public class BomService {

    private final BillOfMaterialsRepository bomRepository;
    private final MasterDataQuery masterDataQuery;

    public BomService(BillOfMaterialsRepository bomRepository, MasterDataQuery masterDataQuery) {
        this.bomRepository = bomRepository;
        this.masterDataQuery = masterDataQuery;
    }

    public record ComponentInput(Long componentItemId, BigDecimal qtyPer, BigDecimal scrapPct) {
    }

    @Transactional
    public BillOfMaterials createBom(Long parentItemId, BigDecimal outputQty,
                                     List<ComponentInput> components, String actor) {
        if (masterDataQuery.findItem(parentItemId).isEmpty()) {
            throw new ManufacturingValidationException("unknown parent item " + parentItemId);
        }
        if (components == null || components.isEmpty()) {
            throw new ManufacturingValidationException("a BOM needs at least one component");
        }
        int version = bomRepository.findByParentItemId(parentItemId).stream()
                .mapToInt(BillOfMaterials::getVersion).max().orElse(0) + 1;
        BillOfMaterials bom = new BillOfMaterials(parentItemId, version, outputQty);
        for (ComponentInput component : components) {
            if (masterDataQuery.findItem(component.componentItemId()).isEmpty()) {
                throw new ManufacturingValidationException(
                        "unknown component item " + component.componentItemId());
            }
            bom.addComponent(component.componentItemId(), component.qtyPer(), component.scrapPct());
        }
        return bomRepository.saveAndFlush(bom);
    }

    @Transactional(readOnly = true)
    public BillOfMaterials getBom(Long id) {
        return bomRepository.findById(id).orElseThrow(() -> new BomNotFoundException(id));
    }
}
