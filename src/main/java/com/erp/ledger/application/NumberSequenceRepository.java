package com.erp.ledger.application;

import com.erp.ledger.domain.NumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NumberSequenceRepository extends JpaRepository<NumberSequence, String> {

    /** Takes a pessimistic write lock on the counter row so concurrent posters serialize on it only. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NumberSequence s where s.scope = :scope")
    Optional<NumberSequence> lockByScope(@Param("scope") String scope);
}
