package com.erp.masterdata.application;

import com.erp.masterdata.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    Optional<Partner> findByCode(String code);

    boolean existsByCode(String code);

    List<Partner> findByVendorTrueOrderByCode();

    List<Partner> findByCustomerTrueOrderByCode();
}
