# Bundle slimming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or subagent-driven-development) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the single ~890 KB JS chunk (the `LARGE_BARREL_MODULES` / ">500 KB chunk" build warning) into route-level + vendor chunks so first load is lighter, ahead of adding the Plus Jakarta Sans webfont in the redesign.

**Architecture:** Two deterministic wins — (1) lazy-load the 8 module pages + audit + 4 print routes with `React.lazy` + `Suspense` so each route (and the `@tabler/icons` it pulls) is its own chunk; (2) a `manualChunks` split so Mantine / React / TanStack vendor code is a stable separate chunk. Optional third win: rewrite the `@tabler/icons-react` barrel to per-icon paths via the rolldown transform-imports plugin (the build message's own suggestion).

**Tech Stack:** Vite 8 (rolldown), React 19, React Router 7, TypeScript strict. No backend, no new runtime deps (the transform plugin is a devDependency).

**Verification model:** This is build configuration — there are no red/green unit tests. Each task is verified by `npm run build` output (chunk count/sizes, absence of the warning) and `npm run test:run` staying green. Run from `frontend/`.

> **Outcome (2026-06-30):** Tasks 2–3 done — route split (890 KB → 414 KB, >500 KB warning gone) + vendor manualChunks (app entry 55 KB). **Task 4 deferred**: route-split already cleared the runtime warning; the `LARGE_BARREL_MODULES` note is build-*speed* only (build is ~350 ms), and the transform plugin needs validating two unknowns (tabler `.mjs` default-export + Vite 8 rolldown plugin registration) — not worth it for a cosmetic note. Revisit only if build time becomes a problem.

---

### Task 1: Baseline the current bundle

**Files:** none (measurement only)

- [ ] **Step 1: Record the current chunk size + warning**

Run: `cd frontend && npm run build`
Expected: one `dist/assets/index-*.js` ≈ 890 KB (gzip ≈ 256 KB) and the warning `Some chunks are larger than 500 kB` + the `LARGE_BARREL_MODULES` note. Note the exact size to compare against later.

---

### Task 2: Route-level code splitting

**Files:**
- Modify: `frontend/src/app/router.tsx`

- [ ] **Step 1: Replace eager page imports with `React.lazy`**

Keep the auth/layout/print-shell wrappers eager (they are tiny and always needed); lazy-load the page components. Replace lines 6–20 (the page imports) so the 8 module pages, `AuditPage`, and the 4 print pages become lazy:

```tsx
import { lazy, Suspense } from 'react';
import { createBrowserRouter, Outlet } from 'react-router-dom';
import { Center, Loader } from '@mantine/core';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { RequireRole } from '../auth/RequireRole';
import { AppLayout } from '../components/AppLayout';

const DashboardPage = lazy(() => import('../pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const LoginPage = lazy(() => import('../pages/LoginPage').then((m) => ({ default: m.LoginPage })));
const NotFoundPage = lazy(() => import('../pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));
const MasterDataPage = lazy(() => import('../features/masterdata/MasterDataPage').then((m) => ({ default: m.MasterDataPage })));
const PurchasingPage = lazy(() => import('../features/purchasing/PurchasingPage').then((m) => ({ default: m.PurchasingPage })));
const SalesPage = lazy(() => import('../features/sales/SalesPage').then((m) => ({ default: m.SalesPage })));
const ManufacturingPage = lazy(() => import('../features/manufacturing/ManufacturingPage').then((m) => ({ default: m.ManufacturingPage })));
const InventoryPage = lazy(() => import('../features/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const ReportsPage = lazy(() => import('../features/reporting/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const LedgerPage = lazy(() => import('../features/ledger/LedgerPage').then((m) => ({ default: m.LedgerPage })));
const AuditPage = lazy(() => import('../features/audit/AuditPage').then((m) => ({ default: m.AuditPage })));
const SalesInvoicePrint = lazy(() => import('../features/print/SalesInvoicePrint').then((m) => ({ default: m.SalesInvoicePrint })));
const PurchaseOrderPrint = lazy(() => import('../features/print/PurchaseOrderPrint').then((m) => ({ default: m.PurchaseOrderPrint })));
const DeliveryPrint = lazy(() => import('../features/print/DeliveryPrint').then((m) => ({ default: m.DeliveryPrint })));
const TrialBalancePrint = lazy(() => import('../features/print/TrialBalancePrint').then((m) => ({ default: m.TrialBalancePrint })));
```

> Note: these components use **named** exports, so the `.then((m) => ({ default: m.X }))` adapter is required (`React.lazy` expects a default export).

- [ ] **Step 2: Wrap the route element trees in a `Suspense` fallback**

Both `<Outlet />` groups render lazy pages, so wrap each lazy boundary. Simplest: wrap the two inner `element` trees. Change the print-shell and app-shell `element`s to include Suspense around their `<Outlet />`/`<AppLayout />`:

```tsx
const fallback = (
  <Center h="100vh">
    <Loader color="terracotta" />
  </Center>
);
```

For the print group `element`:
```tsx
element: (
  <RequireAuth>
    <Suspense fallback={fallback}>
      <Outlet />
    </Suspense>
  </RequireAuth>
),
```

For the app-shell group `element`:
```tsx
element: (
  <RequireAuth>
    <Suspense fallback={fallback}>
      <AppLayout />
    </Suspense>
  </RequireAuth>
),
```

And wrap the public `/login` element:
```tsx
{ path: '/login', element: <Suspense fallback={fallback}>{<LoginPage />}</Suspense> },
```

> `color="terracotta"` is forward-looking; until the redesign theme lands, `color="indigo"` (current `primaryColor`) is fine — use whatever the current `theme.ts` `primaryColor` is to avoid a Mantine "unknown color" console warning. At this point that is `indigo`.

- [ ] **Step 3: Verify build splits into per-route chunks**

Run: `cd frontend && npm run build`
Expected: many `dist/assets/*.js` chunks (one per lazy page) instead of one monolith; the `index` entry chunk is much smaller. The 500 KB warning may still fire for the shared Mantine vendor chunk — Task 3 addresses that.

- [ ] **Step 4: Verify tests still pass**

Run: `cd frontend && npm run test:run`
Expected: `Test Files 6 passed (6)`, `Tests 36 passed`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/router.tsx
git commit -m "重構: 路由層 code-split(React.lazy)拆分單一大 chunk"
```

---

### Task 3: Vendor manualChunks split

**Files:**
- Modify: `frontend/vite.config.ts`

- [ ] **Step 1: Add a `build.rollupOptions.output.manualChunks` to group vendor code**

Add a `build` block to the existing `defineConfig({...})` (keep the `test` block and `plugins`/`server` untouched):

```ts
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-mantine': ['@mantine/core', '@mantine/hooks', '@mantine/form', '@mantine/dates', '@mantine/modals', '@mantine/notifications'],
          'vendor-query': ['@tanstack/react-query'],
        },
      },
    },
  },
```

> Vite 8 (rolldown) honours `build.rollupOptions.output.manualChunks` for back-compat. If a build error reports the key is unsupported, move the same map under `build.rolldownOptions.output.advancedChunks.groups` (rolldown's native form) — verify which the installed version accepts in Step 2.

- [ ] **Step 2: Verify the vendor chunks appear and the entry shrinks**

Run: `cd frontend && npm run build`
Expected: distinct `vendor-react-*.js`, `vendor-mantine-*.js`, `vendor-query-*.js` chunks; the app entry chunk is small; the route chunks contain only page code. The Mantine vendor chunk may still exceed 500 KB (it is genuinely large) — that is acceptable as a cached, stable chunk; record the new largest-chunk size vs the Task 1 baseline.

- [ ] **Step 3: Verify tests still pass**

Run: `cd frontend && npm run test:run`
Expected: 36 passed.

- [ ] **Step 4: Commit**

```bash
git add frontend/vite.config.ts
git commit -m "重構: vendor manualChunks(react/mantine/query 分離快取)"
```

---

### Task 4: Tabler icon barrel transform (kills the `LARGE_BARREL_MODULES` warning)

**Files:**
- Modify: `frontend/package.json` (devDependency)
- Modify: `frontend/vite.config.ts`

- [ ] **Step 1: Install the rolldown transform-imports plugin**

Run: `cd frontend && npm install -D @rolldown/plugin-transform-imports`
Expected: added to devDependencies; lockfile updated; 0 vulnerabilities.

- [ ] **Step 2: Wire it so `@tabler/icons-react` named imports rewrite to per-icon paths**

In `vite.config.ts`, import and register the plugin under the rolldown options so the barrel module is never loaded:

```ts
import { transformImports } from '@rolldown/plugin-transform-imports';
```

Add to `build` (alongside the manualChunks from Task 3):

```ts
  build: {
    rollupOptions: { output: { manualChunks: { /* …from Task 3… */ } } },
    rolldownOptions: {
      plugins: [
        transformImports({
          '@tabler/icons-react': {
            transform: '@tabler/icons-react/dist/esm/icons/{{member}}.mjs',
          },
        }),
      ],
    },
  },
```

> Primary path above follows the plugin's member-template form. If the build reports the per-icon path can't be resolved (the v3 file layout differs), the **fallback** is to drop this plugin and instead change the 19 `import { IconX } from '@tabler/icons-react'` sites to the supported subpath `import { IconX } from '@tabler/icons-react/dist/esm/icons/IconX.mjs'` — but only adopt the fallback if Step 3 fails; do NOT do both.

- [ ] **Step 3: Verify the barrel warning is gone**

Run: `cd frontend && npm run build`
Expected: the `[LARGE_BARREL_MODULES] node_modules/@tabler/icons-react/...` note no longer appears; icons are pulled per-component into their route chunks. If the plugin path errors, apply the Step 2 fallback and re-run.

- [ ] **Step 4: Verify tests + the app still renders icons (type/build proxy)**

Run: `cd frontend && npm run test:run && npm run test:types`
Expected: 36 passed; types clean. (Visual icon rendering is confirmed later on the Oracle/Docker demo — the sandbox can't serve the app.)

- [ ] **Step 5: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts
git commit -m "重構: @tabler/icons barrel 改 per-icon 匯入(消除 LARGE_BARREL 警告)"
```

---

### Task 5: Final verification + PR

- [ ] **Step 1: Full frontend gate**

Run: `cd frontend && npm run build && npm run test:run && npm run test:types`
Expected: build green with multiple chunks and no >800 KB monolith / no barrel warning; 36 tests pass; types clean. Record the before/after largest-chunk size in the PR description.

- [ ] **Step 2: Update docs**

Tick the bundle-slimming item in `PROGRESS.md` 待辦/已完成 (one line: route split + vendor chunks + tabler per-icon imports; before/after sizes).

- [ ] **Step 3: Branch, commit docs, push + PR (ask the owner before push/merge per project rules)**

```bash
git checkout -b refactor/bundle-slimming   # if not already on a feature branch
git add PROGRESS.md && git commit -m "文件: 記錄 bundle 瘦身"
# Then ask the owner before: git push -u origin refactor/bundle-slimming && gh pr create ...
```

---

## Self-review

- **Spec coverage:** Implements spec §10 Phase 1 "Bundle slimming groundwork (audit/route-split…) so the webfont add is net-neutral." Route split (Task 2) + vendor chunks (Task 3) + tabler barrel (Task 4) cover it.
- **Placeholders:** None — the one conditional (Task 4 plugin path vs manual-subpath fallback) gives concrete config for both branches, chosen by the Step 3 build result, not left "TBD".
- **Type consistency:** lazy adapters use the components' real named exports (`DashboardPage`, `SalesPage`, … `TrialBalancePrint`) confirmed against `router.tsx`. `manualChunks` package names match `package.json` deps.
- **Risk note:** Vite 8/rolldown config keys (`manualChunks` vs `advancedChunks`; the transform plugin path template) are the only uncertain points — both have an in-task verification + fallback, so a wrong guess fails loudly at `npm run build`, not silently.
