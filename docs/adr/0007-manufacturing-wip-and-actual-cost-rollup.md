# ADR 0007 — Manufacturing: WIP clearing and actual-cost roll-up

- Status: Accepted
- Date: 2026-06-27

## Context

A work order consumes raw materials and produces a finished good. Two questions: where does the value
sit while production is in progress, and at what cost are the finished goods received? Standard-cost
systems receive finished goods at a predetermined cost and book the difference to manufacturing variance.
With perpetual moving-average valuation ([ADR 0002](0002-moving-average-valuation.md)) there is no
standard — the finished good's cost is simply what its inputs actually cost.

## Decision

Use the **WIP control account (1320) as a clearing account** and receive finished goods at **rolled actual
cost**, reusing the inventory posting port for every leg.

- **Issue** moves each component STOCK → PRODUCTION_WIP at moving-average cost
  (`MANUFACTURING_ISSUE`, counter rule → 1320), posting `Dr WIP / Cr component inventory`. The consumed
  value is captured on the work-order component for the roll-up. Component inventory accounts are resolved
  by item type, so the same machinery handles raw and intermediate items.
- **Completion** receives the finished good WIP → STOCK at
  `rolledCost = total consumed WIP / qty produced` (`MANUFACTURING_RECEIPT`, counter rule → 1320), posting
  `Dr Finished Goods / Cr WIP`. WIP nets to zero once the order completes. Any sub-unit rounding residual
  (received value vs consumed value) is swept to **5930 Manufacturing Variance** so 1320 reaches exactly
  zero — the same odd-cent discipline as the vendor bill's rounding account. For integer quantities the
  residual is zero.
- **Cancellation** of an in-progress order returns the issued components STOCK ← WIP at their issue cost
  (`MANUFACTURING_RETURN`, counter rule → 1320), reversing the issue and zeroing WIP. Like all corrections
  it is append-only: reverse movements and entries, never edits.

The WIP control account is verified to net to zero per work order, and the inventory subledger reconciles
to its GL control accounts after the cycle (proven by `MfgReconciliationIT`).

## Consequences

- **Positive:** one valuation story — finished-good cost is the true cost of its inputs; WIP is a visible,
  self-clearing control account (the manufacturing analogue of GR-IR / Deferred-COGS); every leg reuses the
  inventory port, so the cost-state lock order and reconciliation invariants hold automatically. A reorder
  report reads on-hand through a dedicated `inventory.api.InventoryQuery` port rather than the cost-state
  entity.
- **Negative:** no labour or overhead in WIP (material-only) and no standard-cost variance analysis — both
  consciously deferred. Multi-level BOMs recurse over the same two tables but are not yet exercised. WIP is
  tracked through the GL control account (1320), not as a per-item on-hand position.

## Alternatives considered

- **Receive finished goods at standard cost with a variance account:** rejected for the MVP — it implies a
  standard-cost system we do not maintain; actual moving-average cost is consistent with the rest of the
  ledger.
- **Post WIP directly to the GL without inventory legs:** rejected — routing every movement through the
  inventory port keeps the subledger, the moving average and the lock order consistent across all modules.
