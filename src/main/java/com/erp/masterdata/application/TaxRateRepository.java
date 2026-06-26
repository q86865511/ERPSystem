package com.erp.masterdata.application;

import com.erp.masterdata.domain.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, String> {
}
