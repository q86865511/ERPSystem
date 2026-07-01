package com.erp.hr.api;

/** Published read view of a department — what other modules (e.g. payroll → GL) need without the entity. */
public record DepartmentView(Long id, String code, String name, String budgetAccountCode, boolean active) {
}
