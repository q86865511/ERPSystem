package com.erp.hr.web;

import com.erp.hr.domain.Position;

import java.math.BigDecimal;

/** API view of a position. */
public record PositionResponse(Long id, String code, String title, BigDecimal standardSalary, boolean active) {

    public static PositionResponse from(Position p) {
        return new PositionResponse(p.getId(), p.getCode(), p.getTitle(), p.getStandardSalary(), p.isActive());
    }
}
