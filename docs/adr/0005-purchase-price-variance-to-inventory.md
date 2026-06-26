# ADR 0005 — Purchase price variance revalues inventory (no PPV account)

- Status: Accepted
- Date: 2026-06-26

## Context

A vendor bill may price goods differently from the cost at which they were received (the PO price used at
goods receipt). Standard-cost systems book this difference to a Purchase-Price-Variance (PPV) expense
account. With perpetual moving-average valuation ([ADR 0002](0002-moving-average-valuation.md)) there is no
standard to vary from — the difference is simply a correction to what the inventory actually cost.

## Decision

Route the price variance **into inventory value** (no PPV account in the MVP), keeping the subledger and the
moving average correct.

- The bill's journal entry debits the item's inventory control account for the variance
  (`billed_goods − GR-IR_cleared`), so it balances by accounting identity:
  `Dr GR-IR (receipt cost) + Dr Inventory (variance) + Dr Input VAT = Cr AP`.
- In the **same transaction**, the bill calls the inventory port's `revalue(itemId, valueDelta, ...)`,
  which adjusts `ItemCostState.total_value` (and re-derives the average) and appends a **zero-quantity**
  STOCK leg to the append-only subledger carrying the same value delta. This preserves the Phase 1
  invariants: `total_value == SUM(value_delta of STOCK legs)`, and inventory subledger value == GL Inventory
  control balance.
- `revalue` runs **before** the bill posts its journal entry, so the cost-state lock is taken before the
  journal sequence — the same lock order as a receipt — and a bill-with-variance cannot deadlock against a
  concurrent receipt on the same item.
- `Item.valuation_method` is retained; standard cost with explicit PPV/manufacturing variance accounts is a
  consciously deferred, additive v2.

## Consequences

- **Positive:** the moving average reflects the true landed cost; one valuation story, no parallel
  standard-cost machinery; reconciliation invariants hold automatically (proven by the variance test and the
  reconciliation tests). Concurrency-safe by construction.
- **Negative:** the price variance is not separately reported as a P&L line (it lives in the inventory value
  and flows to COGS when sold) — a deliberate MVP trade-off; teams that need PPV visibility would add the v2
  standard-cost path. The zero-quantity revaluation leg carries a null journal-entry link (it is traceable
  through its `VENDOR_BILL` source document, which holds the journal entry).

## Alternatives considered

- **Dedicated PPV (5910) expense account:** rejected for the MVP — it belongs to standard costing, which is
  out of scope; adding it now would imply a standard we don't maintain.
- **Adjust the average without a subledger leg:** rejected — it would break the
  `cache == SUM(subledger)` invariant the reconciliation health-check relies on.
- **Post the variance as a second inventory movement with quantity:** rejected — it would double-count
  on-hand quantity; the variance is value-only.
