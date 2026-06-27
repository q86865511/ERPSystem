package com.erp.payments.application;

import com.erp.payments.domain.Payment;
import com.erp.payments.domain.PaymentDirection;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayNumber(String payNumber);

    List<Payment> findByDirection(PaymentDirection direction, Sort sort);
}
