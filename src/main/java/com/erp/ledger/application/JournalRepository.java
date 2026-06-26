package com.erp.ledger.application;

import com.erp.ledger.domain.Journal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    Optional<Journal> findByCode(String code);
}
