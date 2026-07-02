package com.erp.reporting.application;

import com.erp.reporting.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByPeriodYearOrderByAccountCode(int periodYear);

    boolean existsByPeriodYearAndAccountCode(int periodYear, String accountCode);
}
