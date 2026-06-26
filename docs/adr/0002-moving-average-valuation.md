# ADR 0002 — Inventory valuation: perpetual moving weighted-average

- Status: Accepted
- Date: 2026-06-26

## Context

The inventory subledger must value stock and post the cost of every movement to the General Ledger so
that inventory subledger value reconciles **exactly** to the GL Inventory control account. The MVP runs a
single base unit of measure and a single currency. The valuation method has to be simple to reason about,
correct under concurrency, and exact to the cent.

## Decision

Use **perpetual moving weighted-average** cost.

- `stock_ledger_entry` is the immutable, append-only source of truth: every movement appends signed
  `qty_delta` / `value_delta` legs; on-hand and value are always `SUM()` of legs, never edited in place
  (a DB trigger blocks UPDATE/DELETE).
- `item_cost_state` is a per-item cache of `on_hand_qty`, `avg_unit_cost`, `total_value`, maintained under
  a pessimistic row lock. **Receipt:** `total_value += qty × unitCost`, average re-derived. **Issue:** valued
  at the current average, average unchanged — except a full drain to zero on-hand removes the exact
  remaining value so the cache lands precisely on zero.
- **Precision split:** quantity and unit cost are `NUMERIC(19,6)`; *value* is money at `NUMERIC(19,4)`.
  Because the subledger `value_delta` and the GL posting share the money scale, the subledger reconciles to
  the GL with no rounding gap. The two-line adjustment entry (inventory vs counter, equal amounts) is
  balanced by construction, so no rounding-difference leg is needed in Phase 1.
- **MVP policy:** negative on-hand is blocked (moving average is meaningless under negative stock);
  standard-cost and purchase-price/manufacturing variances are reserved as an additive v2
  (`Item.valuation_method` column already exists).

## Consequences

- **Positive:** one number per item, trivially explainable; cache and subledger provably consistent
  (a reconciliation test asserts `item_cost_state.total_value == SUM(value_delta)` of STOCK legs and
  subledger value == GL balance); exact to the cent.
- **Negative:** moving average loses per-lot history (no FIFO layers) and cannot express price variances —
  consciously deferred. The cache must be updated under a lock, serialising movements per item.

## Alternatives considered

- **FIFO / specific-lot:** rejected for the MVP — lot layers add schema and complexity without changing the
  reconciliation story the portfolio is built to demonstrate; a candidate v2.
- **Standard cost + variances:** rejected for the MVP — needs a variance-accounting chapter (PPV, mfg
  variance) that is additive later; kept out of the critical path.
- **Recompute average from the subledger on every read:** rejected — O(history) per movement; the cached
  `item_cost_state` with a reconciliation check gives O(1) writes and still proves correctness.
