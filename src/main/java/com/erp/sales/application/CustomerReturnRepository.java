package com.erp.sales.application;

import com.erp.sales.domain.CustomerReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerReturnRepository extends JpaRepository<CustomerReturn, Long> {

    Optional<CustomerReturn> findByReturnNumber(String returnNumber);
}
