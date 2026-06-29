# ADR 0009 — Year-end hard close and retained-earnings carry-forward

- Status: Accepted
- Date: 2026-06-29

## Context

Phase 5 shipped **soft close** only: a fiscal period toggles OPEN↔CLOSED, and the posting service refuses
entries dated in a non-OPEN period. Retained earnings were computed **dynamically** by the reporting module
(balance-sheet equity carries "current-period earnings" = Σrevenue − Σexpense), with no posted year-end
entry. `FiscalPeriodStatus.LOCKED` and a `3200 Retained Earnings` account existed but were unused — reserved
for a real year-end close.

## Decision

Add a **year-end hard close** (`FiscalYearService.closeYear`): post a single closing journal entry that
zeroes the year's revenue and expense accounts and carries the net to **Retained Earnings (3200)**, then
**LOCK every period** of the year (a locked period cannot be reopened or posted into — not even a reversal).

- **Single-step closing entry, by fiscal-year date range.** The closing entry debits each revenue account
  and credits each expense account by its balance **scoped to the year's `[startDate, endDate]`** (not a
  cumulative as-of total), with the net to 3200 (credit for a profit, debit for a loss). Scoping to the year
  range — rather than `accountBalances(asOf)` — keeps the close correct when multiple years coexist (it closes
  only that year's activity) and keeps tests isolated. Abnormal balances flip to the opposite side; accounts
  with a zero balance are skipped; a year with no profit/loss activity locks its periods without posting an
  entry (a zero/one-line entry would violate the ≥2-line balanced-entry invariant).
- **Posts through the one posting entry point.** The closing entry goes through `LedgerPostingService` like
  any other (gapless numbering, balance check, idempotency keyed `(YEAR_END_CLOSE, yearCode, CLOSE)`). Since
  posting requires the target period OPEN, `closeYear` reopens the final period within the same transaction if
  it was soft-closed, posts, then locks.
- **No reporting change.** Zeroing the revenue/expense accounts makes the reporting module's dynamic
  "current-period earnings" fall to zero after the close while 3200 holds the carried-forward earnings — the
  balance sheet stays balanced with no change to `ReportingService`.

## Consequences

- **Positive:** completes the period-close story with a real, posted carry-forward; `LOCKED` is enforced
  (reopen of a locked period and re-closing a closed year are rejected, 422); the closing entry is an
  ordinary balanced JE, auditable in the general ledger; idempotent. No schema migration needed (LOCKED, 3200
  and `fiscal_year` CLOSED already existed).
- **Negative:** retained earnings are not split into "opening retained earnings" vs "current-period earnings"
  on the balance sheet after a close (the dynamic line simply shows zero) — a presentation refinement that
  would need reporting to know fiscal-year boundaries, deferred. The close locks the whole year at once (no
  partial year-end), and there is no "opening balances" entry for the new year beyond the carried-forward 3200.

## Alternatives considered

- **Two-step close via an Income Summary account:** the textbook revenue/expense → Income Summary → Retained
  Earnings. Rejected — there is no Income Summary account in the chart and the single-step transfer to 3200 is
  equivalent and simpler for this scale.
- **Cumulative `accountBalances(asOf)` as the close source:** simpler, and self-consistent for a single first
  year, but pollutes across years once multiple years coexist (and breaks test isolation). Rejected in favour
  of the by-year-range query.
- **Keeping retained earnings purely dynamic (no posted close):** the prior state — fine for reporting, but it
  never locks the books or records a real carry-forward, which is the point of a year-end close.
