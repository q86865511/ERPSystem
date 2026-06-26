package com.erp.masterdata.domain;

import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.PostingRuleRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * Configuration mapping a stock movement to the ledger account code it posts to, so accounts are not
 * hard-coded in the inventory posting path. An INVENTORY-role rule is keyed by {@code itemType} (the
 * control account, e.g. RAW→1310); a COUNTER-role rule is keyed by {@code movementType} (the offset,
 * e.g. ADJUSTMENT_IN/OUT→6000). The unused key column is null for that role, enforced by two partial
 * unique indexes.
 */
@Entity
@Table(name = "inventory_posting_rule")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class InventoryPostingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostingRuleRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type")
    private ItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type")
    private InventoryMovementType movementType;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    public InventoryPostingRule(PostingRuleRole role, ItemType itemType,
                                InventoryMovementType movementType, String accountCode) {
        this.role = role;
        this.itemType = itemType;
        this.movementType = movementType;
        this.accountCode = accountCode;
    }
}
