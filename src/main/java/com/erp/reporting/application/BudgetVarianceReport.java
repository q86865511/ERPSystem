package com.erp.reporting.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** Budget vs actual GL movement for one month, per budgeted account. */
public record BudgetVarianceReport(int year, int month, List<Line> lines) {

    /** One account's budgeted figure, its actual posted movement, and the variance (actual − budget). */
    @Schema(name = "BudgetVarianceLine")
    public record Line(String accountCode, String name, BigDecimal budget, BigDecimal actual,
                       BigDecimal variance) {
    }
}
