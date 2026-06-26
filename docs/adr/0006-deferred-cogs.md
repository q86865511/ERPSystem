# ADR 0006 — Deferred COGS: recognise cost at invoicing, not at delivery

- Status: Accepted
- Date: 2026-06-27

## Context

In order-to-cash, goods physically leave stock at **delivery**, but revenue is recognised at
**invoicing** — and the two events can straddle a period. Where should the cost of the shipped goods land
in between? Two defensible models:

- **Cost-at-delivery:** the delivery posts `Dr COGS / Cr Finished Goods` immediately. Simple, but COGS can
  be recognised in a different period than the matching revenue (a matching-principle violation), and there
  is no symmetry with the procure-to-pay side.
- **Deferred COGS:** the delivery parks the cost in a clearing account; the invoice moves it to COGS at the
  same moment it recognises revenue.

The procure-to-pay side already uses a clearing account — GR-IR (2150),
[ADR 0004](0004-gr-ir-clearing-and-three-way-match.md) — so goods-received-not-invoiced is visible and nets
to zero over a full cycle. Order-to-cash deserves the same treatment for goods-shipped-not-invoiced.

## Decision

Recognise COGS at **invoicing**, deferring the shipped cost in a new asset clearing account
**`1340` Deferred COGS (Shipped Not Invoiced)** — the sales-side mirror of GR-IR.

- **Delivery** issues stock STOCK→CUSTOMER through the inventory port at moving-average cost (movement type
  `SHIPMENT`, counter rule → 1340), posting `Dr 1340 / Cr Finished Goods`. The shipped unit cost is recorded
  on the delivery line (using the movement's actual cost, not the post-issue average, which is 0 after a full
  drain).
- **Invoice** recognises revenue at the sales price (`Dr 1200 AR / Cr 4100 Revenue, Cr 2400 Output VAT`) and,
  in the same balanced entry, recognises COGS by **FIFO-matching delivered-but-uninvoiced delivery lines** and
  clearing their cost (`Dr 5100 COGS / Cr 1340`). This is the structural mirror of how a vendor bill clears
  GR-IR. A fully shipped-and-invoiced order leaves 1340 at zero.
- **Customer return** (credit note) reverses the whole cycle: goods come back (`SALES_RETURN`, counter → 1340,
  `Dr Finished Goods / Cr 1340`) and the credit note reverses revenue + Output VAT + AR and un-recognises COGS
  (`Dr 1340 / Cr 5100`), netting every account to zero.

The AP/AR control accounts carry the partner dimension; the AR subledger (open invoice balances) reconciles
to GL 1200.

## Consequences

- **Positive:** revenue and COGS are recognised together (matching principle); goods-shipped-not-invoiced is a
  reportable balance that nets to zero over a cycle — the same correctness signal as GR-IR, proven by
  `ArReconciliationIT` and `CustomerReturnIT`. The sales and purchasing flows are structurally symmetric, so
  the same FIFO-clearing machinery and reasoning apply on both sides.
- **Negative:** an extra clearing account and a delivery↔invoice matching step versus the simpler
  cost-at-delivery model. Sub-unit rounding when a line's deferred cost does not divide evenly is carried at
  the movement's posted value (so 1340 still nets exactly across a delivery and its invoice).

## Alternatives considered

- **Cost-at-delivery (`Dr COGS / Cr Inventory` on shipment):** rejected — violates revenue/cost matching when
  delivery and invoice fall in different periods, and breaks the GR-IR symmetry that makes the books easy to
  reason about.
- **Honouring `partner.ar_account_code` overrides for AR:** deferred — like the AP side, the MVP uses the
  single 1200 control account so the subledger reconciles to one balance; per-partner control accounts are an
  additive change.
