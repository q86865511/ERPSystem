package com.erp.hr.web;

import com.erp.hr.domain.Department;

/** API view of a department. */
public record DepartmentResponse(Long id, String code, String name, String budgetAccountCode, boolean active) {

    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getBudgetAccountCode(), d.isActive());
    }
}
