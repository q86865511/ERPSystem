package com.erp.ledger.application;

import com.erp.ledger.domain.FiscalYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FiscalYearRepository extends JpaRepository<FiscalYear, Long> {

    Optional<FiscalYear> findByCode(String code);
}
