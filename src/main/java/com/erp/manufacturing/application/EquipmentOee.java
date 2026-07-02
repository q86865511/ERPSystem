package com.erp.manufacturing.application;

import java.math.BigDecimal;

/**
 * One machine's OEE, as percentages (0–100): Availability × Performance × Quality, and their product
 * OEE. Availability = run time / planned time; Performance = actual vs. ideal output while running;
 * Quality = good units / produced units.
 */
public record EquipmentOee(Long equipmentId, String code, String name, BigDecimal availability,
                           BigDecimal performance, BigDecimal quality, BigDecimal oee) {
}
