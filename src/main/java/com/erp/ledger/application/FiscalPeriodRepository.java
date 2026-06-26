package com.erp.ledger.application;

import com.erp.ledger.domain.FiscalPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface FiscalPeriodRepository extends JpaRepository<FiscalPeriod, Long> {

    @Query("select p from FiscalPeriod p where :date between p.startDate and p.endDate")
    Optional<FiscalPeriod> findByDate(@Param("date") LocalDate date);
}
