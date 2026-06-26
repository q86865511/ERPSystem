package com.erp.inventory.application;

import com.erp.inventory.domain.StockLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockLedgerEntryRepository extends JpaRepository<StockLedgerEntry, Long> {

    List<StockLedgerEntry> findByMovementGroupId(UUID movementGroupId);

    List<StockLedgerEntry> findByItemId(Long itemId);
}
