package com.erp.inventory.application;

import com.erp.inventory.api.StockMovementCommand;
import com.erp.inventory.api.StockMovementResult;
import com.erp.inventory.api.StockPosting;
import com.erp.inventory.domain.ItemCostState;
import com.erp.inventory.domain.StockLedgerEntry;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import com.erp.masterdata.api.InventoryMovementType;
import com.erp.masterdata.api.ItemView;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.LocationView;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.shared.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Posts a balanced two-leg stock movement and its journal entry in one transaction. The lock order is
 * fixed across every posting path — the item's {@link ItemCostState} row (FOR UPDATE) first, then the
 * ledger's journal-entry sequence (taken last, innermost, inside {@link LedgerPosting#post}) — so
 * concurrent movements serialize on the item without ever deadlocking. The stock legs, the cost-state
 * update and the journal entry commit or roll back together.
 */
@Service
public class StockPostingService implements StockPosting {

    private final MasterDataQuery masterDataQuery;
    private final LedgerPosting ledgerPosting;
    private final ItemCostStateRepository itemCostStateRepository;
    private final StockLedgerEntryRepository stockLedgerEntryRepository;

    public StockPostingService(MasterDataQuery masterDataQuery,
                               LedgerPosting ledgerPosting,
                               ItemCostStateRepository itemCostStateRepository,
                               StockLedgerEntryRepository stockLedgerEntryRepository) {
        this.masterDataQuery = masterDataQuery;
        this.ledgerPosting = ledgerPosting;
        this.itemCostStateRepository = itemCostStateRepository;
        this.stockLedgerEntryRepository = stockLedgerEntryRepository;
    }

    @Override
    @Transactional
    public StockMovementResult post(StockMovementCommand command, String actor) {
        ItemView item = masterDataQuery.findItem(command.itemId())
                .orElseThrow(() -> new StockMovementValidationException("unknown item " + command.itemId()));
        if (!item.stocked()) {
            throw new ItemNotStockedException(item.sku());
        }
        LocationView fromLocation = requireLocation(command.fromLocationId());
        LocationView toLocation = requireLocation(command.toLocationId());

        if (command.qty() == null || command.qty().signum() <= 0) {
            throw new StockMovementValidationException("movement quantity must be positive");
        }
        Quantity qty = Quantity.of(command.qty());

        boolean intoStock = toLocation.type() == LocationType.STOCK;
        boolean outOfStock = fromLocation.type() == LocationType.STOCK;
        if (intoStock == outOfStock) {
            // Phase 1 movements have exactly one STOCK side (stock ↔ virtual location).
            throw new StockMovementValidationException(
                    "a movement must have exactly one STOCK location (from="
                            + fromLocation.type() + ", to=" + toLocation.type() + ")");
        }

        // Lock the cost-state row first; the ledger takes the JE sequence lock last (see class doc).
        itemCostStateRepository.ensureExists(command.itemId());
        ItemCostState costState = itemCostStateRepository.lockByItemId(command.itemId())
                .orElseThrow(() -> new IllegalStateException(
                        "cost state vanished for item " + command.itemId()));

        BigDecimal unitCost;
        BigDecimal value;
        if (intoStock) {
            unitCost = command.unitCost() != null ? command.unitCost() : item.standardCost();
            value = costState.applyReceipt(qty, unitCost);
        } else {
            if (costState.wouldGoNegative(qty)) {
                throw new NegativeInventoryException(
                        command.itemId(), costState.getOnHandQty(), qty.amount());
            }
            unitCost = costState.getAvgUnitCost();
            value = costState.applyIssue(qty);
        }

        String inventoryAccount = masterDataQuery.resolveInventoryAccountCode(item.itemType());
        String counterAccount = masterDataQuery.resolveCounterAccountCode(command.movementType());

        // The STOCK leg's value sign drives the entry: stock up → Dr inventory / Cr counter; down → reverse.
        JournalEntryRequest journalRequest = new JournalEntryRequest(
                null, command.postingDate(), command.memo(), null,
                command.sourceDocType(), command.sourceDocId(), command.movementType().name(),
                intoStock
                        ? List.of(debit(inventoryAccount, value, command.memo()),
                                  credit(counterAccount, value, command.memo()))
                        : List.of(debit(counterAccount, value, command.memo()),
                                  credit(inventoryAccount, value, command.memo())));

        PostingResult posting = ledgerPosting.post(journalRequest, actor);

        // Persist the cost-state update and append the two immutable legs, linking the journal entry
        // before the first flush (the append-only table forbids a later UPDATE).
        itemCostStateRepository.save(costState);

        UUID movementGroupId = UUID.randomUUID();
        Instant postedAt = Instant.now();
        StockLedgerEntry fromLeg = new StockLedgerEntry(movementGroupId, command.itemId(),
                command.fromLocationId(), qty.amount().negate(), unitCost, value.negate(),
                command.movementType(), command.sourceDocType(), command.sourceDocId(),
                posting.entryId(), postedAt);
        StockLedgerEntry toLeg = new StockLedgerEntry(movementGroupId, command.itemId(),
                command.toLocationId(), qty.amount(), unitCost, value,
                command.movementType(), command.sourceDocType(), command.sourceDocId(),
                posting.entryId(), postedAt);
        stockLedgerEntryRepository.saveAll(List.of(fromLeg, toLeg));
        stockLedgerEntryRepository.flush();

        return new StockMovementResult(movementGroupId, posting.entryId(), posting.entryNo(),
                costState.getOnHandQty(), costState.getAvgUnitCost());
    }

    @Override
    @Transactional
    public void revalue(Long itemId, BigDecimal valueDelta, String sourceDocType, String sourceDocId) {
        if (valueDelta == null || valueDelta.signum() == 0) {
            return;
        }
        ItemCostState costState = itemCostStateRepository.lockByItemId(itemId)
                .orElseThrow(() -> new IllegalStateException(
                        "cannot revalue item " + itemId + " with no cost state"));
        costState.applyRevaluation(valueDelta);
        itemCostStateRepository.save(costState);

        Long stockLocationId = stockLocationForItem(itemId);
        InventoryMovementType type = valueDelta.signum() > 0
                ? InventoryMovementType.ADJUSTMENT_IN : InventoryMovementType.ADJUSTMENT_OUT;
        StockLedgerEntry leg = new StockLedgerEntry(UUID.randomUUID(), itemId, stockLocationId,
                BigDecimal.ZERO, BigDecimal.ZERO, valueDelta, type, sourceDocType, sourceDocId,
                null, Instant.now());
        stockLedgerEntryRepository.saveAndFlush(leg);
    }

    /** Finds a STOCK-type location that already holds the item (where its value can be revalued). */
    private Long stockLocationForItem(Long itemId) {
        return stockLedgerEntryRepository.findByItemId(itemId).stream()
                .map(StockLedgerEntry::getLocationId).distinct()
                .filter(locationId -> masterDataQuery.findLocation(locationId)
                        .map(location -> location.type() == LocationType.STOCK).orElse(false))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no STOCK location holding item " + itemId + " to revalue"));
    }

    private LocationView requireLocation(Long locationId) {
        if (locationId == null) {
            throw new StockMovementValidationException("location id is required");
        }
        return masterDataQuery.findLocation(locationId)
                .orElseThrow(() -> new StockMovementValidationException("unknown location " + locationId));
    }

    private static JournalEntryRequest.Line debit(String accountCode, BigDecimal amount, String memo) {
        return new JournalEntryRequest.Line(accountCode, amount, null, memo);
    }

    private static JournalEntryRequest.Line credit(String accountCode, BigDecimal amount, String memo) {
        return new JournalEntryRequest.Line(accountCode, null, amount, memo);
    }
}
