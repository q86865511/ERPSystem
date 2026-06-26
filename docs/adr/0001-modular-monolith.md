# ADR 0001 — Modular monolith with a shared ledger kernel

- Status: Accepted
- Date: 2026-06-26

## Context

This is a from-scratch manufacturing ERP built solo as a portfolio project. It must demonstrate clean
architecture and ERP-domain correctness, ship in incremental milestones, and remain understandable and
testable by one person. The domain naturally splits into modules (ledger, inventory, purchasing, sales,
manufacturing, payments, reporting) that must integrate tightly — every business action ultimately posts
to the General Ledger.

## Decision

Build a **modular monolith**: a single deployable backend over a single PostgreSQL database, organised
into in-process modules whose boundaries are *enforced*, not merely suggested.

- Module = Java package under `com.erp.<module>` with internal layers `domain` / `application` / `api` /
  `web`. A module may expose only its `api` package to others.
- No module imports another module's internal packages or touches its tables. This is verified in CI with
  **ArchUnit** so the "modular" claim is mechanically true, not aspirational.
- The `ledger` module is a **shared kernel**. Every value-bearing action posts through the single
  `LedgerPostingService`; business code never writes `journal_entry`/`journal_line` directly.
- Cross-module posting is a **direct synchronous call** inside the same `@Transactional` method as the
  document and inventory writes (see ADR on posting/transaction boundaries). In-process domain events are
  reserved for genuinely asynchronous concerns (audit log, notifications).

## Consequences

- **Positive:** ~90% of the design-clarity signal of microservices at ~10% of the operational cost; one
  transaction boundary spanning document + inventory + ledger, which is the core correctness story; easy
  local run and testing; boundaries provable in CI.
- **Negative:** all modules share one process and one database, so independent scaling/deployment is not
  possible without future extraction. Accepted — not a requirement for this project.
- **If extraction is ever needed**, the enforced `api`-only boundaries make peeling a module into its own
  service tractable.

## Alternatives considered

- **Microservices**: rejected — operational overhead (service discovery, distributed transactions,
  eventual consistency) is unjustifiable for a solo project and would obscure the atomic-posting story.
- **Unstructured monolith**: rejected — no enforced boundaries means the "modular" claim cannot be proven
  and the codebase erodes into a big ball of mud.
