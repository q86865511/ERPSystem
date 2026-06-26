package com.erp.inventory.application;

import com.erp.inventory.domain.ItemCostState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemCostStateRepository extends JpaRepository<ItemCostState, Long> {

    /**
     * Idempotently inserts a zero cost-state row for an item. {@code ON CONFLICT DO NOTHING} makes
     * concurrent first-movements safe without exception handling; the subsequent
     * {@link #lockByItemId(Long)} then locks the (now-existing) row.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO item_cost_state (item_id, on_hand_qty, avg_unit_cost, total_value, version) "
            + "VALUES (:itemId, 0, 0, 0, 0) ON CONFLICT (item_id) DO NOTHING", nativeQuery = true)
    void ensureExists(@Param("itemId") Long itemId);

    /** Takes a pessimistic write lock on the item's cost-state row so concurrent movements serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ItemCostState c where c.itemId = :itemId")
    Optional<ItemCostState> lockByItemId(@Param("itemId") Long itemId);
}
