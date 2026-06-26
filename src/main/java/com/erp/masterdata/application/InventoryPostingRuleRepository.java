package com.erp.masterdata.application;

import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.PostingRuleRole;
import com.erp.masterdata.domain.InventoryPostingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryPostingRuleRepository extends JpaRepository<InventoryPostingRule, Long> {

    Optional<InventoryPostingRule> findByRoleAndItemType(PostingRuleRole role, ItemType itemType);

    Optional<InventoryPostingRule> findByRoleAndMovementType(PostingRuleRole role,
                                                             InventoryMovementType movementType);
}
