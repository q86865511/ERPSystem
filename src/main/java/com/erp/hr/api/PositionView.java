package com.erp.hr.api;

import java.math.BigDecimal;

/** Published read view of a position. */
public record PositionView(Long id, String code, String title, BigDecimal standardSalary, boolean active) {
}
