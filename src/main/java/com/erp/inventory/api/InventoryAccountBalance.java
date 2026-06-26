package com.erp.inventory.api;

import java.math.BigDecimal;

/** Published view of the inventory subledger value rolled up to one GL inventory control account. */
public record InventoryAccountBalance(String accountCode, BigDecimal value) {
}
