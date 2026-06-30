# ADR 0010 — Journal-entry reversal (correcting entries)

- Status: Accepted
- Date: 2026-06-30

## Context

The ledger is append-only and immutable: a POSTED `journal_entry`/`journal_line` cannot be edited or deleted
(DB triggers in V1 enforce it). So a correction must be a **new reversing entry** that offsets the original,
never an edit. V1 already provisioned the columns for this (`reverses_entry_id`, `reversed_by_entry_id`) and
the immutability trigger explicitly permits setting them on a POSTED row — but no service or endpoint used
them, and there was no way to fetch a single entry with its lines (the frontend approximated detail via
per-account GL drill-down).

## Decision

Add `LedgerPostingService.reverse(entryNo, reversalDate, memo, actor)` plus `GET /api/ledger/journal-entries/{entryNo}`
and `POST /api/ledger/journal-entries/{entryNo}/reverse` (ACCOUNTANT). The reversal mirrors the original
line-for-line with **debit/credit swapped verbatim** and posts through the one `LedgerPostingService.post`
entry point, inheriting every invariant (balance, gapless number, period-OPEN, the posted event consumed by
audit + metrics).

- **Manual-only.** Reversal is refused (422) when `source_doc_type` is non-null. Reversing a subledger-sourced
  GL leg without reversing the owning document would desync the AP/AR/Inventory subledgers from their GL
  control accounts and break the reconciliation health-check. Document corrections go through the owning
  module (e.g. the existing customer-return), never this endpoint.
- **Both entries stay POSTED; "reversed" is a link, not a status.** The original is marked only by setting
  `reversed_by_entry_id`; its status stays `POSTED`. Every balance/reporting query filters `status='POSTED'`,
  so the original and its mirror both remain and **net to zero**. Flipping the original to `REVERSED` would
  drop it from those queries while the mirror also subtracts — a 2× movement that corrupts the trial balance,
  reconciliation, and the year-end rollforward. The `REVERSED` enum value is left unused by balance queries.
- **Reversal lands in an OPEN period.** The reversal date defaults to today (or a caller-supplied date) and
  must resolve to an OPEN period via the normal post path — the original's (possibly now CLOSED/LOCKED) period
  is never reused or circumvented.
- **Guards, in order:** not found → 404; not POSTED → 422; document-sourced → 422; already reversed → 422
  (`reversed_by_entry_id` set in the same transaction, so a concurrent second reversal fails the guard). The
  reversal carries **no source-key triple**, so it never collides with the document idempotency index.
- **No migration.** The columns, statuses, and trigger allowance already exist in V1.

## Consequences

- **Positive:** completes the "every correction is itself a balanced, auditable posting" story; the by-entry
  GET fills a real frontend gap; reuses the posting path so all invariants and the audit/metrics events come
  for free; zero schema change; the manual-only guard makes the reconciliation invariant un-breakable through
  this surface.
- **Negative:** subledger-document entries are not reversible here (by design — they reverse through their
  module); there is no full entries browser (target is selected by entry number); reversing a reversal is
  allowed (it simply re-posts the original) only because each link guard still holds.

## Alternatives considered

- **Flip the original to `REVERSED`:** matches the enum's intent but, given every balance query filters
  `status='POSTED'`, would require changing all of them to `IN ('POSTED','REVERSED')` in lockstep or silently
  corrupt balances. Rejected — the link-only marker is safer and needs no query changes.
- **Allow reversing any entry (incl. subledger-sourced):** maximally flexible but breaks subledger==GL
  reconciliation. Rejected; document reversals belong to their owning module.
- **A new migration for the link/marker:** unnecessary — V1 already has the columns and the trigger permits
  the update.
