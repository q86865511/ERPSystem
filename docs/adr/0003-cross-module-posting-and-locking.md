# ADR 0003 — Cross-module posting via published ports, typed-location inventory, and lock order

- Status: Accepted
- Date: 2026-06-26

## Context

Phase 1 adds the first cross-module posting path: an inventory movement must update the moving-average
cost, append the immutable subledger legs, and post a balanced journal entry — atomically. This has to
respect the enforced module boundaries ([ADR 0001](0001-modular-monolith.md)), keep inventory "double-entry"
in its own right, and stay correct under concurrent movements without deadlocking.

## Decision

**1. Published ports, not internal calls.** A module integrates with another only through that module's
`api` package. `ledger` exposes `LedgerPosting.post(JournalEntryRequest) → PostingResult` (a thin adapter
over the internal `LedgerPostingService`, which still returns the rich `JournalEntry` to same-module
callers) and `SequenceAllocator.next(scope)` for shared document numbering. `inventory` exposes
`StockPosting`. Cross-module references pass values (an account **code**, an id), never another module's
entities. ArchUnit forbids `inventory → ledger.{domain,application,web}` and the masterdata equivalents.

**2. Inventory is itself double-entry via typed locations.** Every movement writes **two** `stock_ledger_entry`
legs sharing a `movement_group_id`, one per location, so `SUM(qty_delta)` and `SUM(value_delta)` over a
movement are zero. Real stock lives in STOCK locations; the counterparties are typed virtual locations
(INVENTORY_LOSS, later VENDOR/CUSTOMER/PRODUCTION_WIP). Only the STOCK-location leg feeds `item_cost_state`.
A stock adjustment is STOCK ↔ INVENTORY_LOSS: a gain posts `Dr Inventory / Cr 6000`, a loss the reverse.

**3. One transaction, fixed lock order.** `StockPostingService.post` runs in a single `@Transactional`
method: lock `item_cost_state` (`SELECT … FOR UPDATE`, items in id order) → recompute average → resolve
accounts via masterdata → call `LedgerPosting.post`, which takes the gapless journal-entry sequence lock
**last** (a single global row, always innermost). Because that sequence is always acquired last and no path
acquires it before an item lock, there is no lock cycle — concurrent movements on the same item serialise on
its cost-state row with no deadlock. First-movement row creation uses `INSERT … ON CONFLICT DO NOTHING` to
avoid a create race. Account mapping lives in masterdata's `InventoryPostingRule` (not hard-coded).

## Consequences

- **Positive:** document + inventory legs + journal entry commit or roll back together (a closed-period
  rejection from the ledger rolls back the whole movement — proven by test); boundaries are mechanically
  enforced; concurrency is correct and deadlock-free (proven by a concurrent-gain test); inventory balances
  internally and to the GL.
- **Negative:** serialising on the per-item cost-state row caps throughput per item — acceptable, and the
  correct trade for exact moving-average cost. The published-port indirection adds a small amount of glue
  (adapter + result records).

## Alternatives considered

- **Domain events as the primary posting mechanism:** rejected — it blurs the transaction boundary that is
  the project's core correctness story; events are reserved for genuinely async concerns (audit,
  notification).
- **Global `SERIALIZABLE` isolation:** rejected — `READ COMMITTED` + explicit, ordered pessimistic locks is
  cheaper and makes the concurrency argument explicit and testable.
- **A single signed stock leg instead of two:** rejected — paired legs keep inventory genuinely
  double-entry and reuse one movement shape for receipts, issues and transfers in later phases.
