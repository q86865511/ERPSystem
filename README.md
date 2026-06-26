# Manufacturing ERP

[![CI](https://github.com/q86865511/ERPSystem/actions/workflows/ci.yml/badge.svg)](https://github.com/q86865511/ERPSystem/actions/workflows/ci.yml)

<p align="center"><img src="docs/cover.png" alt="Manufacturing ERP — cover" width="100%"></p>

A from-scratch **manufacturing ERP** built as a portfolio project. Its soul is a hand-written
**double-entry General Ledger**: every business action — goods receipt, production, shipment,
payment — posts a balanced journal entry **in the same database transaction** as the document and
inventory change. Nothing is ever half-posted; an unbalanced entry cannot be committed.

> **Why build from scratch instead of extending Odoo/ERPNext?** Because the deliverable is evidence
> of *architecture and ERP-domain literacy* — a posting engine with a hard debit=credit invariant, an
> immutable inventory subledger that reconciles to a GL control account, and a
> document → inventory → ledger pipeline defensible line by line. Extending a packaged ERP would
> mostly demonstrate framework configuration. (Extending would be the right call for shipping real
> business value fast, or when targeting an explicit Odoo/Frappe role — neither applies here.)

## The headline demo — buy → make → sell

One vertical slice exercises the document → inventory → ledger loop three times and lands the
manufacturing differentiator (single-level BOM + Work Order with correct WIP accounting):

1. **Buy** a raw material — Purchase Order → Goods Receipt → Vendor Bill → Payment
2. **Make** a finished good — single-level BOM → Work Order (consume raw into WIP, produce finished good)
3. **Sell** it — Sales Order → Delivery (ship at cost) → Invoice (revenue + COGS) → Receipt

Then **prove it**: the *reconciliation health-check* asserts the books are correct —
global `SUM(debit) = SUM(credit)`, inventory subledger value == GL Inventory control balance,
AR/AP subledgers == their control accounts, and every clearing account (GR-IR, WIP) nets to zero
after a complete cycle. This one report is the project's hero artifact.

## Architecture

<p align="center"><img src="docs/architecture.svg" alt="Manufacturing ERP architecture" width="900"></p>

A **modular monolith**: single deployable, single PostgreSQL database, in-process modules whose
boundaries are *enforced* (no module imports another's internals or touches its tables — checked in
CI with ArchUnit). The `ledger` module is a shared kernel exposing a published `LedgerPosting` port;
every other module posts through it **in the same transaction** as its own writes, never reaching into
ledger internals. `inventory` keeps a moving weighted-average subledger that reconciles to the GL Inventory
control account; `purchasing` and `payments` drive the procure-to-pay chain (receipt → bill → payment) with
GR-IR clearing and an AP subledger that reconciles to its control account. See [docs/adr/](docs/adr/) for the
architecture decision records.

| Layer | Choice |
|---|---|
| Backend | Java 21 + Spring Boot 4 |
| Database | PostgreSQL 16 |
| Persistence | Spring Data JPA / Hibernate + Flyway migrations |
| Boundary enforcement | ArchUnit (CI-enforced) |
| Money & quantity | `BigDecimal` value objects — `Money` (`NUMERIC(19,4)`) and `Quantity`/cost (`NUMERIC(19,6)`), never `float` |
| Auth | Spring Security + stateless JWT, role-based |
| Testing | JUnit + Testcontainers (real Postgres) |
| Frontend | React 18 + TypeScript + Mantine *(later phase)* |

## Running it

Prerequisites: **JDK 21**, **Docker** (for the database / Testcontainers).

```bash
# run the test suite (spins a throwaway Postgres via Testcontainers)
./mvnw test

# run the app locally (Spring Boot Docker Compose auto-starts the postgres service)
./mvnw spring-boot:run
```

> On Windows the Maven Wrapper needs `powershell` on PATH and `JAVA_HOME` set; see
> [PROGRESS.md](PROGRESS.md) for the exact environment notes used during development.

## Roadmap

**✅ Done:** Phase 0 walking skeleton (ledger spine) · Phase 1 products & inventory (moving weighted-average
valuation, append-only subledger reconciling to the GL Inventory control account) · Phase 2 procure-to-pay
(PO → goods receipt → vendor bill → payment, with GR-IR clearing, Input VAT, a purchase-price variance that
revalues inventory, and an AP subledger that reconciles to its control account) · Phase 3 order-to-cash
(Sales Order → Delivery → Invoice → Receipt, plus customer returns / credit notes). Costs follow a
**deferred-COGS** model mirroring GR-IR: a delivery parks the shipped cost in a Deferred-COGS clearing
account (`Dr 1340 / Cr Finished Goods`); the invoice recognises COGS against it (`Dr COGS / Cr 1340`)
alongside revenue + Output VAT, so a fully shipped-and-invoiced order leaves the clearing account at zero.
The chain reconciles end-to-end (inventory falls, COGS/revenue/Output VAT post, Deferred-COGS and AR net to
zero, trial balance balances, AR subledger == its control account); customer receipts reuse the `payments`
module (`direction IN`), an AR-aging report is exposed, and a customer return posts a credit note that
reverses the whole cycle to zero. · Phase 4 manufacturing (single-level BOM → Work Order → WIP issue →
completion at rolled actual cost, plus work-order cancellation). A work order snapshots its BOM at
release, issues components into WIP (`Dr WIP / Cr component inventory` at moving-average cost), and
completes by receiving finished goods at the rolled cost (`Dr Finished Goods / Cr WIP`, any sub-unit
residual swept to Manufacturing Variance) — so WIP nets to zero over the cycle. A reorder-point report
lists items at or below their reorder point.

**🎉 Minimum show-worthy milestone reached** (end of Phase 4): the full *buy → make → sell* slice runs
end-to-end with the books reconciling at every step.

**✅ Also done:** Phase 5 reporting & period close. A read-side `reporting` module composes the ledger's
published balances into financial statements — a trial balance (as of a date), an income statement, and a
balance sheet whose equity carries current-period earnings (retained earnings computed dynamically, no
year-end close) — plus general-ledger drill-down. It also exposes the **reconciliation health-check** at
`GET /api/reporting/reconciliation` — the project's hero artifact — which composes each module's published
subledger balance with the GL to assert the books are correct: the trial balance balances and the
inventory, AP and AR subledgers each equal their GL control account (with the GR-IR / Deferred-COGS / WIP
clearing accounts reported alongside). Fiscal periods can be **soft-closed** (and reopened) — the posting
service refuses any entry dated in a non-open period.

**Next:** Phase 6 polish & packaging — fuller RBAC, a one-key real-posting demo seed, and the final
README (architecture diagram, ERD, ADRs).

Full arc: Phase 0 (ledger spine) → 1 products & inventory → 2 procure-to-pay → 3 order-to-cash →
**4 manufacturing (minimum show-worthy milestone)** → 5 reporting & period close → 6 polish & packaging.
Full plan and consciously-deferred scope are tracked in [PROGRESS.md](PROGRESS.md).

### Consciously deferred (deliberate scoping, not gaps)
Multi-currency/FX, multi-tenancy (never), FIFO/standard-cost variances, tax engine beyond one VAT
line, approval workflows, full time-phased MRP, lot/serial tracking, routings/work-centers/labor,
multi-warehouse transfers, multi-level BOM.
