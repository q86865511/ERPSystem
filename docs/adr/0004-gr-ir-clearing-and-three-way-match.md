# ADR 0004 — GR-IR clearing and lightweight three-way match

- Status: Accepted
- Date: 2026-06-26

## Context

In procure-to-pay, goods usually arrive before the supplier's invoice. The receipt must record the
inventory asset and a liability immediately, and the later bill must settle that liability and establish
accounts payable — without double-counting and without blocking the business when quantities or prices
don't match perfectly. The bridge is a GR-IR (goods-received/invoice-received) clearing account.

## Decision

Use **2150 GR-IR Clearing** as the bridge, cleared by matching bills to receipts.

- **Goods receipt** posts `Dr Inventory / Cr GR-IR` at the PO unit cost — driven through the existing
  inventory posting port (a RECEIPT movement VENDOR → STOCK), so the moving average and the inventory
  control account update with it. The receipt credits GR-IR.
- **Vendor bill** matches its lines (FIFO over received-but-unbilled receipt lines) and posts
  `Dr GR-IR / Dr Input VAT / Cr Accounts Payable`. The GR-IR debit equals the sum of `matched_qty ×
  receipt_unit_cost` — exactly the credits the matched receipts made — so **GR-IR nets to zero once a
  receipt is fully billed**. `qty_billed` on the receipt and PO lines is the watermark for partial billing.
- **Three-way match is advisory, never a gate.** The bill records a `match_status` (MATCHED /
  PRICE_VARIANCE) for reporting but always posts. The single hard invariant is that the GR-IR debit equals
  the matched receipt credit; quantity/price differences are handled (price variance — see
  [ADR 0005](0005-purchase-price-variance-to-inventory.md)), not rejected.
- **The AP subledger is the authoritative payable**: the sum of open vendor-bill balances, asserted equal
  to the GL 2100 control account by a reconciliation test (`ApReconciliationIT`). Journal lines on GR-IR
  and AP carry the vendor as a `partner_id` analytic dimension.

## Consequences

- **Positive:** receipt and invoice are decoupled in time but provably consistent — a full procure-to-pay
  cycle leaves GR-IR at zero and AP reconciled to its control account (proven end-to-end by test). Partial
  receipts and partial bills work via the per-line watermark.
- **Negative:** the matching logic (FIFO allocation, watermarks) is more code than posting a flat invoice,
  and a permanently under-billed receipt leaves a real GR-IR balance that must be monitored — acceptable,
  and exactly what GR-IR is for.

## Alternatives considered

- **No GR-IR (bill posts inventory directly):** rejected — it cannot represent received-but-unbilled goods,
  which is the normal state between receipt and invoice.
- **Hard three-way-match gate (block on any mismatch):** rejected for the MVP — it stops the books from
  reflecting reality; advisory matching plus a variance path keeps posting truthful.
