# ADR 0008 — Role-based access control via request authorization

- Status: Accepted
- Date: 2026-06-27

## Context

Phase 0 shipped thin authentication — a single in-memory ADMIN over stateless HTTP Basic — and deferred
real RBAC. The business has four natural roles: **ACCOUNTANT** (financial postings — vendor bills,
customer invoices, payments, manual journal entries, period close), **WAREHOUSE** (physical movements —
goods receipts, deliveries, stock adjustments, production), **SALES** (sales orders), and **ADMIN**
(master data, superuser). The roadmap suggested method-level `@PreAuthorize` at the application-service
layer so "rules don't differ by entry point."

## Decision

Enforce RBAC as **request authorization centralised in `SecurityConfig`** (URL + HTTP-method rules),
over HTTP Basic, with four in-memory users (one per role; `admin` holds all four roles).

- Write endpoints are gated by role — e.g. `POST /api/purchasing/vendor-bills` → ACCOUNTANT,
  `POST /api/purchasing/**` → WAREHOUSE, `POST /api/sales/sales-orders/**` → SALES,
  `POST /api/masterdata/**` → ADMIN. Read endpoints (GETs, all reports) need only authentication.
- `admin` is granted every role, so it passes every check (a superuser) without a role hierarchy.

The API has a **single entry point** (REST), so request-level authorization is equivalent to
service-level guards in practice, while keeping all the rules in one auditable place and — importantly —
not forcing a security context into the ~50 service-level integration tests (which call services directly
and would otherwise all need authentication wiring).

## Consequences

- **Positive:** the whole authorization policy is one readable block in `SecurityConfig`; adding a role
  or endpoint is a one-line change; the existing service/posting tests are unaffected; an end-to-end test
  (`RbacIT`, MockMvc over the real filter chain) proves 401 (unauthenticated), 403 (wrong role) and
  authorized access per role.
- **Negative:** authorization lives at the web boundary, so a future *non-REST* entry point (a scheduled
  job, a message listener) would need its own guard — at which point method-level `@PreAuthorize` on the
  application services would be the right addition. Permissions are role-coarse (no per-field/verb
  granularity), and users are in-memory.

## Alternatives considered

- **Method-level `@PreAuthorize` on application services:** the roadmap's preference, rejected for the MVP
  because it would require every service-direct integration test to establish a security context (and a
  test dependency), for no extra protection given the single REST entry point. It remains the natural next
  step if non-REST entry points appear.
- **Stateless JWT with a persisted user/role store:** deferred — Basic over a stateless API is enough to
  demonstrate the RBAC model; tokens and a user store are an additive enhancement, not a correctness
  concern for the portfolio.
