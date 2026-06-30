# Warm Terracotta — UI/UX Redesign Design Spec

**Status:** Approved direction (design locked with owner) — ready for phased implementation
**Date:** 2026-06-30
**Scope:** `E:/ClaudeWorkingPlace/ERPSystem/frontend` (React 19 + Vite 8 + Mantine 9 + React Router 7 + TanStack Query 5 + TypeScript 5.9, strict). 100% frontend — no backend changes.

---

## 1. Context & goals

The portfolio manufacturing-ERP frontend currently ships a 9-line theme stub (`primaryColor: 'indigo'`, `defaultRadius: 'md'`, one `fontFamily`), the default Vite favicon, and a header with only a language toggle. Pages and panels are functional but visually generic. This spec defines a cohesive **"Warm Terracotta"** redesign that:

- Establishes a bespoke design layer **on top of Mantine** (rich theme + `theme.components` overrides + a few new shared components) — not a from-scratch component library.
- Re-skins **every page and panel** to the warm palette, light-first with a matching warm-dark mode and a header scheme toggle.
- Folds **accessibility** (focus traps, icon-button `aria-label`s, contrast) into the redesign rather than deferring it.
- Adds a designed **web icon** (favicon + header logo mark, terracotta, accounting/ledger motif) and a hand-rolled **first-time onboarding tour**.
- Respects a parallel **bundle-slimming** goal: any new font/dependency must justify its weight or be hand-rolled.

### Key de-risking finding (drives the whole plan)

**Feature panels contain ZERO hardcoded colors.** A grep for `#hex`, `backgroundColor`, `color:'…'`, and `var(--mantine…)` across `src/features` returns **0 matches** — colors are expressed only as Mantine props (`color=`, `variant=`, `c=`). Consequences:

- The terracotta accent and the dark palette propagate almost entirely from `theme.ts` + `theme.components`. **Most panels are re-skinned for free by theme inheritance** and need no per-file edits.
- Dark-mode parity is mostly Mantine's job **provided we never introduce raw hex** in the new theme/components — derive both schemes from the single Mantine `colors` ramp + `light-dark()` / `var(--mantine-color-*)`.
- "Redo every page" therefore means **every page looks redone** (mostly via inheritance), not *every file hand-edited*. Hand-touch is reserved for heroes, dashboards, and dense forms.

### Real size (grounded in the tree)

- 41 feature `.tsx` files, ~5,276 LOC under `src/features/`, plus 5 shared components and 4 top-level pages. Heaviest panels are dynamic-row forms: `WorkOrdersPanel` (360), `SalesInvoicesPanel` (317), `VendorBillsPanel` (302), `PurchaseOrdersPanel` (269).
- i18n: core `en.ts` (166) + `zh-TW.ts` (158) + 10 module fragments (~1,089 lines). Every new string is a **2-file edit** (`en` + `zh-TW`) or `tsc` fails — `zh-TW.ts` is typed `: Messages`.

---

## 2. Locked decisions (recap — do NOT re-litigate)

- **Direction:** "Warm Terracotta". Light-first warm palette, terracotta accent (~`#C0532E`, full 50→900 ramp) as Mantine `primaryColor`. Warm off-white page (~`#FAF7F4`), white cards, warm-gray borders/text. Semantic colors kept (success=green, danger=red, warning=amber, info=blue).
- **Dark mode kept:** matching warm-dark palette (not pure black), plus a color-scheme toggle in the header next to the language toggle. `defaultColorScheme` stays `"auto"`.
- **Shape language:** soft rounded corners (cards 14–16px, controls ~10px), 0.5px hairline borders, very light shadows, comfortable spacing, slightly larger type.
- **Fonts:** Latin = Plus Jakarta Sans (self-hosted, 2 weights); CJK = system stack `"PingFang TC", "Microsoft JhengHei", "Noto Sans TC"` (NO multi-MB CJK webfont).
- **Approach:** bespoke layer ON TOP of Mantine — keep Mantine primitives for accessibility/behavior.
- **Scope:** every page/panel; a11y folded in; designed favicon + header logo; first-time onboarding tour remembered via localStorage, bilingual.
- **Constraints:** new deps must justify weight (prefer hand-rolled); sandbox cannot run a dev server — verify via `npm run build` + Vitest, click-test on the Docker/Oracle demo.

---

## 3. Design tokens & theme (light + dark)

### 3.1 Terracotta primary ramp (50→900)

The accent `#C0532E` anchors the primary scale, optimized light-first with contrast coverage for both schemes. Mantine `primaryShade` is `6` so the deep `#C0532E` is the interactive default in light mode.

| Shade | Hex | Role |
|------|------|------|
| 50 | `#FDF8F5` | Lightest surface / hover wash |
| 100 | `#F9F0EB` | Very light surface |
| 200 | `#F0DFCC` | Secondary light surface |
| 300 | `#E8C6A5` | Tertiary surface |
| 400 | `#E0AD7E` | Medium surface |
| 500 | `#D4935D` | Light interactive |
| 600 | `#C0532E` | **Primary accent (light)** |
| 700 | `#A64729` | Dark accent / hover |
| 800 | `#8C3C23` | Darkest accent |
| 900 | `#71301E` | Extreme dark (rare) |

> In dark mode the same ramp reads well; Mantine lightens interactive states automatically. Do not author a second hex ramp for dark — let `light-dark()` / `primaryShade` per-scheme handle it.

### 3.2 Warm neutral (page / card / border / text)

Surfaces and text come from a warm-gray scale plus explicit body/card CSS variables.

**Light mode**

| Role | Value |
|------|-------|
| Page background | `#FAF7F4` |
| Card / modal background | `#FFFFFF` (max contrast vs page) |
| Border / divider | `#E8DED7` (0.5px hairline) |
| Text primary | `#4A423A` |
| Text secondary / dimmed | `#9E8976` |

**Dark mode (warm-dark, not pure black)**

| Role | Value |
|------|-------|
| Page background | `#1A1410` |
| Card / surface background | `#2A2420` (one shade lighter) |
| Border / divider | `#3A3430` (0.5px hairline) |
| Text primary | `#F5F1ED` |
| Text secondary / dimmed | `#A09890` |

### 3.3 Semantic colors (kept from Mantine defaults)

| Token | Light | Dark |
|-------|-------|------|
| success (green) | `#2F9E44` | `#69DB7C` |
| danger (red) | `#E03131` | `#FF8787` |
| warning (amber) | `#F08C00` | `#FFD43B` |
| info (blue) | `#1971C2` | `#74C0FC` |

### 3.4 Radius, spacing, shadows

| Token | Value | Use |
|-------|-------|-----|
| `radius.xs` | 4 | tight chips |
| `radius.sm` | 8 | badges, small buttons |
| `radius.md` (default) | 10 | inputs, buttons, selects |
| `radius.lg` | 14 | cards, modals, drawers |
| `radius.xl` | 16 | large hero cards |
| `spacing.xs` | 8 | in-component padding |
| `spacing.sm` | 12 | component padding |
| `spacing.md` | 16 | section gaps |
| `spacing.lg` | 24 | major section separation |
| `spacing.xl` | 32 | layout-level gaps |

Shadows (light-first, slightly stronger opacity in dark to avoid banding):

| Depth | Light | Dark |
|-------|-------|------|
| xs | `0 1px 3px rgba(0,0,0,0.08)` | `0 1px 3px rgba(0,0,0,0.16)` |
| sm | `0 2px 4px rgba(0,0,0,0.10)` | `0 2px 4px rgba(0,0,0,0.20)` |
| md | `0 4px 8px rgba(0,0,0,0.12)` | `0 4px 8px rgba(0,0,0,0.24)` |
| lg | `0 8px 16px rgba(0,0,0,0.15)` | `0 8px 16px rgba(0,0,0,0.28)` |
| xl | `0 16px 32px rgba(0,0,0,0.20)` | `0 16px 32px rgba(0,0,0,0.32)` |

### 3.5 Typography

- **Latin:** Plus Jakarta Sans, self-hosted, **2 weights only (400 / 600)**, `font-display: swap`, `latin` subset, `preload` the body weight. Net add target ~60–90 KB.
- **CJK:** system stack — no webfont.
- **fontFamily:** `'"Plus Jakarta Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", "PingFang TC", "Microsoft JhengHei", "Noto Sans TC", sans-serif'`
- **monospace:** `'"JetBrains Mono", "Courier New", monospace'` (already-installed system mono fine; `Money`/`MoneyText` stays monospace).

| Level | Size | Line-height | Weight |
|-------|------|-------------|--------|
| h1 | 32px | 1.3 | 700 |
| h2 | 28px | 1.35 | 700 |
| h3 | 22px | 1.4 | 600 |
| h4 | 18px | 1.45 | 600 |
| h5 | 16px | 1.5 | 500 |
| h6 | 14px | 1.5 | 500 |
| body | 14px | 1.5 | 400 |

> Self-host the woff2 files (don't hit Google's CDN at runtime) so the Docker demo is offline-safe. Place under `frontend/src/assets/fonts/` and load via an `@font-face` block in `frontend/src/index.css` (or a dedicated `fonts.css`), with a `<link rel="preload" as="font" type="font/woff2" crossorigin>` for the 400 weight in `index.html`.

### 3.6 `theme.ts` skeleton

**File:** `E:/ClaudeWorkingPlace/ERPSystem/frontend/src/theme.ts`

```typescript
import { createTheme, type MantineThemeOverride } from '@mantine/core';

const TERRACOTTA = [
  '#FDF8F5', // 50
  '#F9F0EB', // 100
  '#F0DFCC', // 200
  '#E8C6A5', // 300
  '#E0AD7E', // 400
  '#D4935D', // 500
  '#C0532E', // 600 — primary accent
  '#A64729', // 700
  '#8C3C23', // 800
  '#71301E', // 900
] as const;

// Warm-gray scale (used for borders, dimmed text, dark surfaces).
const WARM_GRAY = [
  '#FCFAF8', // 0
  '#F5EDE8', // 1
  '#E8DED7', // 2  — light border
  '#D9C8B8', // 3
  '#9E8976', // 4  — dimmed text
  '#6B5E52', // 5
  '#4A423A', // 6  — primary text (light)
  '#3A3430', // 7  — dark border
  '#2A2420', // 8  — dark card bg
  '#1A1410', // 9  — dark page bg
] as const;

export const theme = createTheme({
  primaryColor: 'terracotta',
  primaryShade: { light: 6, dark: 5 },
  autoContrast: true,

  colors: {
    terracotta: [...TERRACOTTA],
    gray: [...WARM_GRAY],
    // success/red/yellow/blue kept as Mantine defaults
  },

  defaultRadius: 'md',
  radius: { xs: '4px', sm: '8px', md: '10px', lg: '14px', xl: '16px' },
  spacing: { xs: '8px', sm: '12px', md: '16px', lg: '24px', xl: '32px' },

  shadows: {
    xs: '0 1px 3px rgba(0,0,0,0.08)',
    sm: '0 2px 4px rgba(0,0,0,0.10)',
    md: '0 4px 8px rgba(0,0,0,0.12)',
    lg: '0 8px 16px rgba(0,0,0,0.15)',
    xl: '0 16px 32px rgba(0,0,0,0.20)',
  },

  fontFamily:
    '"Plus Jakarta Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, ' +
    '"Helvetica Neue", "PingFang TC", "Microsoft JhengHei", "Noto Sans TC", sans-serif',
  fontFamilyMonospace: '"JetBrains Mono", "Courier New", monospace',

  headings: {
    fontWeight: '600',
    sizes: {
      h1: { fontSize: '32px', fontWeight: '700', lineHeight: '1.3' },
      h2: { fontSize: '28px', fontWeight: '700', lineHeight: '1.35' },
      h3: { fontSize: '22px', fontWeight: '600', lineHeight: '1.4' },
      h4: { fontSize: '18px', fontWeight: '600', lineHeight: '1.45' },
      h5: { fontSize: '16px', fontWeight: '500', lineHeight: '1.5' },
      h6: { fontSize: '14px', fontWeight: '500', lineHeight: '1.5' },
    },
  },

  components: {
    // See §4 — Component[].extend({...}) overrides live here.
  },
} as MantineThemeOverride);
```

Warm page/card backgrounds that Mantine does not derive automatically are pinned via CSS variables in `frontend/src/index.css`:

```css
:root,
[data-mantine-color-scheme='light'] {
  --mantine-color-body: #FAF7F4;     /* page */
  --app-color-card: #FFFFFF;
  --app-color-border: #E8DED7;
}
[data-mantine-color-scheme='dark'] {
  --mantine-color-body: #1A1410;
  --app-color-card: #2A2420;
  --app-color-border: #3A3430;
}
```

> **Hard rule for dark-mode parity:** no raw hex anywhere in the new shared components — reference the ramp (`color="terracotta"`, `c="dimmed"`, `bg="var(--app-color-card)"`) or `light-dark()`. Both schemes must derive from this one source.

---

## 4. Component system (theme.components overrides + new shared components)

The component layer = global Mantine overrides (visual skin, preserving behavior/a11y) + a small set of new shared components. All live under `frontend/src/`.

> **Strict-TS note:** under `verbatimModuleSyntax` + `noUncheckedIndexedAccess`, author each override with `Component.extend({...})` (not a bare object literal) so `defaultProps`/`classNames`/`vars`/`styles` stay typed. Import types with `import type {...}`. This is the highest typing-pain area — isolate it in its own PR (Phase 2.2) so it never blocks rollout.

### 4.1 Mantine overrides (in `theme.components`)

| Component | Intent |
|---|---|
| **AppShell** | Warm header surface, subtle/no shadow; navbar warm off-white; grouped nav. |
| **Button** | Primary terracotta; outline for secondary; terracotta focus ring; radius `md` (10). |
| **Card** | White (light) / `--app-color-card` (dark); 0.5px warm border; radius `lg` (14); very light shadow. |
| **Paper** | Same as Card, used semantically for form sections. |
| **Table** | Hairline borders; warm `th` (terracotta-tinted, fw 500); warm hover row; no heavy stripes. |
| **Badge** | Light bg + semantic color ramps; radius `sm` (8); 12px. |
| **Tabs** | Active tab underline + label in terracotta; inactive dimmed; no fill. |
| **TextInput / Select / DateInput** | 0.5px warm border; terracotta focus border (outline only); radius `md`; dimmed placeholder. |
| **Modal** | Warm bg, radius `lg`, centered, terracotta close button, `padding="lg"`. |
| **Drawer** | Right position, warm bg, radius `lg`, terracotta close button. |
| **ActionIcon** | Transparent/tertiary; terracotta on hover; radius `md`. |
| **NavLink** | Active: warm wash bg + 3px terracotta left border + terracotta icon; hover warm wash. |
| **Alert** | Semantic tinted bg; radius `md`; 0.5px off-color border. |
| **Tooltip** | Warm-dark bg, white text, radius `sm`. |

### 4.2 Existing shared components — enhance

| Component | Path | Change |
|---|---|---|
| `AppLayout` | `src/components/AppLayout.tsx` | Grouped nav (`NavGroup`); header gains color-scheme toggle + logo mark + Help/restart-tour menu item; `data-onboarding` attrs. |
| `PageHeader` | `src/components/PageHeader.tsx` | Larger type; optional `subtitle`, `breadcrumb`, page-level `status` badge, `actions` slot. |
| `StatusBadge` | `src/components/StatusBadge.tsx` | Map primary states to terracotta; optional icon + size variants; dark-mode-safe semantic ramps. |
| `Money` (`MoneyText`) | `src/components/Money.tsx` | Keep monospace; larger in hero/summary; warm accent in select contexts. |
| `EntitySelect` | `src/components/EntitySelect.tsx` | Inherits new input skin; terracotta dropdown affordance. |

`StatusBadge` color map intent:

```typescript
const COLOR: Record<string, string> = {
  DRAFT: 'gray',
  CONFIRMED: 'terracotta',
  RELEASED: 'terracotta',
  PARTIALLY_RECEIVED: 'yellow',
  RECEIVED: 'teal',
  POSTED: 'teal',
  PAID: 'teal',
  DONE: 'teal',
  CANCELLED: 'red',
  // … remaining keys preserved
};
```

### 4.3 New shared components

| Component | Path | Purpose |
|---|---|---|
| `NavGroup` | `src/components/NavGroup.tsx` | Grouped nav section (small muted label + divider + `NavLink`s). |
| `HeroCard` | `src/components/HeroCard.tsx` | Hero container: large icon + title/subtitle + status badge + nested content. |
| `StatTile` / `StatCard` | `src/components/StatTile.tsx` | KPI tile: label + value (via `MoneyText`) + optional trend/sparkline/icon. |
| `CardWrapper` | `src/components/CardWrapper.tsx` | Standard panel surface with title/footer + loading/empty states. |
| `DataTable` | `src/components/DataTable.tsx` | Generic table wrapper: columns + rows + loading/empty + `onRowClick`. |
| `DetailDrawer` | `src/components/DetailDrawer.tsx` | Right-slide record drawer (header + content + close). |
| `ConfirmationModal` | `src/components/ConfirmationModal.tsx` | Reusable confirm dialog with loading + danger variant. |
| `LoadingOverlay` | `src/components/LoadingOverlay.tsx` | Container overlay spinner (warm backdrop). |
| `EmptyState` | `src/components/EmptyState.tsx` | "No data" placeholder + optional CTA. |
| `StateButton` | `src/components/StateButton.tsx` | State-machine action button (label + disabled-reason + color variant). |
| `StatementSection` | `src/components/StatementSection.tsx` | Hierarchical financial-statement grouping (header + indented items + bold subtotal). |
| `AmountAllocationTable` | `src/components/AmountAllocationTable.tsx` | Payment allocation (invoices/bills + checkboxes + cumulative total, BigInt-safe). |
| `FormSection` | `src/components/FormSection.tsx` | Styled form container (padding/border/radius). |
| `GuestHint` | onboarding overlay (see §6) | First-time guidance. |
| `PrintHeader` | `src/features/print/PrintHeader.tsx` | Print document header (logo + doc#/date/recipient). |

Add a barrel `src/components/index.ts` re-exporting all shared components.

Representative typed signatures:

```typescript
interface DataTableProps<T> {
  columns: Array<{ key: string; label: string; align?: 'left' | 'center' | 'right';
                   width?: string; render?: (row: T) => ReactNode }>;
  rows: T[];
  rowKey: (row: T) => string | number;
  isLoading?: boolean; isEmpty?: boolean; emptyMessage?: string;
  onRowClick?: (row: T) => void; striped?: boolean;
}

interface ConfirmationModalProps {
  title: string; message: string | ReactNode;
  isOpen: boolean; onClose: () => void;
  onConfirm: () => Promise<void> | void;
  isLoading?: boolean; confirmLabel?: string; cancelLabel?: string; isDangerous?: boolean;
}
```

### 4.4 New i18n keys for components

Add to **both** `src/i18n/messages/en.ts` and `src/i18n/messages/zh-TW.ts` (and/or a `modules/` fragment):

```typescript
component: {
  emptyState: { noData: 'No data yet', tryAgain: 'Try again' },
  confirmationModal: { confirm: 'Confirm', cancel: 'Cancel', confirmDelete: 'Delete' },
  pagination: { previous: 'Previous', next: 'Next', page: 'Page {current} of {total}' },
},
common: {
  delete: 'Delete', deleteRow: 'Delete row', close: 'Close', colorScheme: 'Color scheme',
},
theme: { light: 'Light', dark: 'Dark', auto: 'Auto' },
```

---

## 5. Web icon / favicon / logo mark

A flat **two-color mark** evoking ledger/double-entry bookkeeping in balance: a fulcrum triangle, two offset balance arms (debit/credit), and two ledger lines, terracotta on warm off-white. Works from 16px favicon to header logo. No icon library — SVG is inlined/static, bundle-neutral.

### 5.1 Primary mark (`frontend/public/favicon.svg` / `favicon-light.svg`)

```svg
<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
  <!-- Warm background circle -->
  <circle cx="32" cy="32" r="31" fill="#FAF7F4" stroke="#D4C9BD" stroke-width="1"/>

  <!-- Scale fulcrum (center triangle) -->
  <path d="M 32 22 L 28 28 L 36 28 Z" fill="#C0532E"/>

  <!-- Left balance arm (debit ledger) -->
  <rect x="12" y="24" width="16" height="3" rx="1.5" fill="#C0532E" opacity="0.9"/>
  <circle cx="15" cy="25.5" r="1.5" fill="#C0532E" opacity="0.7"/>

  <!-- Right balance arm (credit ledger) -->
  <rect x="36" y="26" width="16" height="3" rx="1.5" fill="#C0532E" opacity="0.85"/>
  <circle cx="49" cy="27.5" r="1.5" fill="#C0532E" opacity="0.7"/>

  <!-- Ledger lines (double-entry records) -->
  <line x1="12" y1="38" x2="52" y2="38" stroke="#C0532E" stroke-width="1.5" opacity="0.6"/>
  <line x1="12" y1="42" x2="52" y2="42" stroke="#C0532E" stroke-width="1.5" opacity="0.5"/>
</svg>
```

**Dark variant** (`frontend/public/favicon-dark.svg`): swap the circle to `fill="#2A1F1A" stroke="#4A3A30"` and all marks to `#E8937D` (warm-light terracotta).

### 5.2 Favicon wiring (`frontend/index.html`)

```html
<title>Manufacturing ERP</title>

<!-- Theme-aware SVG favicons -->
<link rel="icon" type="image/svg+xml" href="/favicon-light.svg" media="(prefers-color-scheme: light)" />
<link rel="icon" type="image/svg+xml" href="/favicon-dark.svg"  media="(prefers-color-scheme: dark)" />
<!-- PNG fallback (optional) -->
<link rel="icon" type="image/png" href="/favicon-192.png" sizes="192x192" />
<link rel="apple-touch-icon" href="/apple-touch-icon.png" />
<meta name="theme-color" content="#C0532E" />

<!-- Preload body weight of Plus Jakarta Sans -->
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="/src/assets/fonts/plus-jakarta-sans-400.woff2" />
```

> `prefers-color-scheme` media on `<link rel="icon">` is not universally honored. Acceptable fallback: ship a single `favicon.svg` and let `<meta name="theme-color">` + the SVG's own warm bg suffice; optionally swap the `<link>` href from `main.tsx` based on Mantine's `colorScheme`.

### 5.3 Header logo (`frontend/src/components/AppLayout.tsx`)

Replace the text-only title with logo mark + wordmark. Inline the SVG using `currentColor` so it adapts to scheme automatically:

```tsx
<Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 8, textDecoration: 'none' }}>
  <svg width={32} height={32} viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden style={{ flexShrink: 0 }}>
    <path d="M 32 22 L 28 28 L 36 28 Z" fill="currentColor"/>
    <rect x="12" y="24" width="16" height="3" rx="1.5" fill="currentColor" opacity="0.9"/>
    <circle cx="15" cy="25.5" r="1.5" fill="currentColor" opacity="0.7"/>
    <rect x="36" y="26" width="16" height="3" rx="1.5" fill="currentColor" opacity="0.85"/>
    <circle cx="49" cy="27.5" r="1.5" fill="currentColor" opacity="0.7"/>
    <line x1="12" y1="38" x2="52" y2="38" stroke="currentColor" stroke-width="1.5" opacity="0.6"/>
    <line x1="12" y1="42" x2="52" y2="42" stroke="currentColor" stroke-width="1.5" opacity="0.5"/>
  </svg>
  <Title order={4} style={{ margin: 0, color: 'var(--mantine-color-terracotta-6)' }}>{t('app.title')}</Title>
</Link>
```

File structure:

```
frontend/public/
  favicon-light.svg
  favicon-dark.svg
  favicon-192.png        (optional)
  apple-touch-icon.png   (optional)
```

---

## 6. First-time onboarding tour

### 6.1 Mechanism — hand-rolled (no tour library)

Build a custom `OnboardingTourProvider` + `useOnboardingTour` hook + a Mantine `Popover`/spotlight overlay. **Do not add** `reactour` / `shepherd` / `driver.js` — they fight the bundle-slimming goal. Hand-rolled cost ≈ **2.5 KB**, zero new deps, full theme coherence, stronger "from-scratch" portfolio narrative. Persistence mirrors the existing `localePreference.ts` pattern.

### 6.2 Files

| File | Change |
|------|--------|
| `src/onboarding/onboardingPreference.ts` | **NEW** — localStorage load/save/reset (try-catch, best-effort), key `erp.onboarding`. |
| `src/onboarding/steps.ts` | **NEW** — `OnboardingStep[]` (id, optional `targetSelector`, title/description/action i18n keys, `position`). |
| `src/onboarding/useOnboardingTour.ts` | **NEW** — context + provider + `next/previous/skip/restart`. |
| `src/onboarding/OnboardingTourOverlay.tsx` | **NEW** — spotlight backdrop + focus ring + `Popover` callout. |
| `src/main.tsx` | Wrap `<RouterProvider>` with `<OnboardingTourProvider>` (after `I18nProvider`). |
| `src/pages/LoginPage.tsx` | `data-onboarding="demo-accounts"` on demo section. |
| `src/pages/DashboardPage.tsx` | wrap `<ReconciliationHero />` in `data-onboarding="reconciliation-hero"`. |
| `src/components/AppLayout.tsx` | `data-onboarding` on `nav-purchasing` + `header-language`; Help/restart-tour menu item. |
| `src/i18n/messages/modules/onboarding.ts` (+ `en`/`zh-TW` wiring) | **NEW** fragment — all tour strings, paired. |

### 6.3 Persisted state

```typescript
// src/onboarding/onboardingPreference.ts
const KEY = 'erp.onboarding';
export interface OnboardingState { completed: boolean; currentStep: number; dismissedAt?: string }
export function loadOnboardingState(): OnboardingState | null { /* try JSON.parse(localStorage[KEY]); catch -> null */ }
export function saveOnboardingState(s: OnboardingState): void { /* try localStorage.setItem; catch -> noop */ }
export function resetOnboardingState(): void { /* try localStorage.removeItem; catch -> noop */ }
```

### 6.4 Steps (3–5, on the key flows)

| # | id | target | content |
|---|----|--------|---------|
| 1 | `login-welcome` | (centered, no target) | Welcome + how to restart later. |
| 2 | `login-demo-accounts` | `[data-onboarding="demo-accounts"]` | admin/accountant/warehouse/sales (password = username) or read-only guest. |
| 3 | `dashboard-reconciliation` | `[data-onboarding="reconciliation-hero"]` | Reconciliation health check — verify before posting. |
| 4 | `nav-modules` | `[data-onboarding="nav-purchasing"]` | Core flow Purchasing → Inventory → Manufacturing → Sales. |
| 5 | `header-toggles` | `[data-onboarding="header-language"]` | Language + light/dark toggles. |

### 6.5 i18n (en.ts; mirror in zh-TW.ts)

```typescript
onboarding: {
  steps: {
    loginWelcome:   { title: 'Welcome to Manufacturing ERP', description: 'This quick tour shows the key features. Restart anytime from the Help menu.' },
    demoAccounts:   { title: 'Try Demo Accounts', description: 'Sign in as admin / accountant / warehouse / sales (password = username), or browse as a read-only guest.' },
    reconciliation: { title: 'Reconciliation Health Check', description: 'Shows whether the books balance. Check subledgers and clearing accounts before posting.' },
    navModules:     { title: 'Navigate Modules', description: 'A typical flow is Purchasing → Inventory → Manufacturing → Sales.', tryIt: 'Try visiting Purchasing' },
    headerToggles:  { title: 'Language & Theme', description: 'Switch English / Traditional Chinese and toggle light/dark mode.' },
  },
  completionMessage: 'Tour complete — explore at your own pace.',
  restartTour: 'Restart tour', nextStep: 'Next', previousStep: 'Back', skipTour: 'Skip',
},
```

### 6.6 Lifecycle & a11y

- Shown on first `/` view when state is absent/`completed === false`.
- Last "Next" → `completed = true`; "Skip" records `dismissedAt`. State persists across logout; restart via Help menu (`resetOnboardingState`).
- A11y: focus-trap the popover buttons; Enter = Next, Escape = Skip; announce "Step X of N"; `aria-label` on the popover.
- Spotlight ring color must use `var(--mantine-color-terracotta-6)` (NOT hardcoded indigo) — derive from theme.

---

## 7. Dark-mode parity + scheme toggle

### 7.1 Toggle control (`AppLayout.tsx` header, beside the language `SegmentedControl`)

```tsx
import { useMantineColorScheme } from '@mantine/core';
const { colorScheme, setColorScheme } = useMantineColorScheme();

<SegmentedControl
  size="xs"
  aria-label={t('common.colorScheme')}
  value={colorScheme}
  onChange={(v) => setColorScheme(v as 'light' | 'dark' | 'auto')}
  data={[
    { label: '☀', value: 'light' },
    { label: '🌙', value: 'dark' },
    { label: '⚙', value: 'auto' },
  ]}
  data-onboarding="header-colorscheme"
/>
```

- **Persistence:** Mantine auto-persists to localStorage (`mantine-color-scheme-value`) — no manual storage.
- **Default:** `defaultColorScheme="auto"` in `main.tsx` stays unchanged (respects OS).
- Icons are Unicode glyphs — no icon-font dependency.

### 7.2 Parity discipline

- All page/card/border backgrounds come from the warm-gray ramp + the `--mantine-color-body` / `--app-color-card` / `--app-color-border` CSS variables (§3.6). The **only** dark-mode trap is hand-written hex in new components — forbidden.
- Verify both schemes via Vitest snapshots of `StatusBadge` and `Money` rendered under `colorScheme="light"` and `"dark"` (cheap guard since the sandbox can't run a dev server).

---

## 8. Accessibility (folded in)

### 8.1 Icon-only ActionIcon `aria-label`s (confirmed offenders)

| File | Line | Icon | Label |
|------|------|------|-------|
| `src/features/purchasing/PurchaseOrdersPanel.tsx` | 174 | IconTrash | `t('common.deleteRow')` |
| `src/features/sales/SalesOrdersPanel.tsx` | 147 | IconTrash | `t('common.deleteRow')` |
| `src/features/ledger/ManualEntryPanel.tsx` | 123 | IconTrash | `t('common.deleteRow')` |
| `src/features/manufacturing/BomsPanel.tsx` | 132 | IconTrash | `t('common.deleteRow')` |

Pattern: add `aria-label={t('common.deleteRow')}` to each icon-only `ActionIcon` (WCAG 1.4.3). Re-grep new shared components for the same.

### 8.2 Focus traps (Modals / Drawers)

Mantine v9 `Modal` and `Drawer` ship focus-trap by default (`trapFocus`) and return focus to the trigger on close — **no custom code**. Audit: open a modal/drawer, Tab cycles within only, Esc closes and restores focus.

### 8.3 Contrast

- StatusBadge semantic colors verified ≥ WCAG AA on both `#FAF7F4` and `#1A1410` backgrounds (Mantine ships AA-compliant ramps). Run a Lighthouse pass post-theme to confirm no regression.
- Terracotta focus ring meets ≥3:1 on all surfaces.

### 8.4 Keyboard checklist

- Logical Tab order; native `<button>/<input>/<a>` (Mantine provides); visible `:focus-visible` outline.
- Esc closes modals/drawers; arrow keys in Select/SegmentedControl; Enter submits forms.
- No keyboard traps; verify whole app navigable mouse-free.

### 8.5 Automated (later, report-only)

Add `axe-playwright` to the existing (non-blocking) e2e or a Lighthouse-CI workflow auditing `/`, `/purchasing`, `/sales`, `/reporting`. Record baseline; do not block `main`.

---

## 9. Per-page redesign inventory (every file, by module)

> Most panels are re-skinned by theme inheritance (zero hardcoded colors). "Redesign actions" below flag where **bespoke layout** or new shared-component adoption is genuinely needed; trivial tables inherit and need no hand-edit.

### Core pages (`src/pages/`)

| # | File | Treatment |
|---|------|-----------|
| 1 | `DashboardPage.tsx` | Hero via `ReconciliationHero` in `HeroCard` (radius 14–16, warm border on healthy); 3 summary `StatTile`s (Assets/Liabilities/Net Income); add `PageHeader`; loader during fetch; `aria-label` on ✓/✗ ThemeIcon; `data-onboarding="reconciliation-hero"`. |
| 2 | `LoginPage.tsx` | Bespoke two-column entry (branded left logo + welcome; right form with larger inputs + terracotta CTA); demo accounts as styled grid; `data-onboarding="demo-accounts"`; first-visit tour entry. |
| 3 | `NotFoundPage.tsx` | Larger terracotta 404 title; warm icon; roomier `Container py`. |
| 4 | `ForbiddenPage.tsx` | As 404, warning/amber-terracotta accent for 403. |

### Master Data (`src/features/masterdata/`)

| # | File | Treatment |
|---|------|-----------|
| 5 | `MasterDataPage.tsx` | Tabs skin (terracotta underline) + `PageHeader`. |
| 6 | `ItemsPanel.tsx` | Create as `Button` w/ icon; `DataTable` (hairline, warm hover, right-aligned money, icon/boolean badges); `EmptyState`; modal form on new input skin. |
| 7 | `PartnersPanel.tsx` | As Items; type badge (vendor/customer/both); type segment control. |
| 8 | `WarehousesPanel.tsx` | As Items (code/name/location). |
| 9 | `LocationsPanel.tsx` | Warehouse filter Select above table; warehouse-select-required modal. |

### Purchasing (`src/features/purchasing/`, payments in `src/features/payments/`)

| # | File | Treatment |
|---|------|-----------|
| 10 | `PurchasingPage.tsx` | Tabs + `PageHeader`. |
| 11 | `PurchaseOrdersPanel.tsx` | `DataTable` + StatusBadge; row → `DetailDrawer` (header + lines + totals via `MoneyText`); Confirm `StateButton` when DRAFT; new-order modal with dynamic lines (`EntitySelect`, qty, price) + read-only totals; `aria-label` on delete ActionIcon. |
| 12 | `GoodsReceiptsPanel.tsx` | Table + `DetailDrawer`; create modal selects CONFIRMED PO, partial qty, shows GR# + journal entry id. |
| 13 | `VendorBillsPanel.tsx` | Table (open balance right-aligned) + drawer (lines + matchStatus badge); create from PO, running totals. |
| 14 | `payments/PaymentsOutPanel.tsx` | Table + create modal using `AmountAllocationTable` (unpaid bills, BigInt-safe). |
| 15 | `ApAgingPanel.tsx` | `StatTile` buckets (Current/30/60/90+) or table; as-of date in `PageHeader`; no create. |

### Sales (`src/features/sales/`, payments in `src/features/payments/`)

| # | File | Treatment |
|---|------|-----------|
| 16 | `SalesPage.tsx` | Tabs + `PageHeader`. |
| 17 | `SalesOrdersPanel.tsx` | As PurchaseOrders (SO#/customer/status; drawer with ordered/shipped/invoiced; Confirm StateButton; `aria-label` delete). |
| 18 | `DeliveriesPanel.tsx` | Table + drawer; "Deferred COGS" hint; create from CONFIRMED SO, partial qty. |
| 19 | `SalesInvoicesPanel.tsx` | Table + drawer (lines incl. COGS; goods/VAT/gross summary); create from SO; Print → `/print/sales-invoice/:id`. |
| 20 | `payments/PaymentsInPanel.tsx` | Table + `AmountAllocationTable` (unpaid invoices). |
| 21 | `CustomerReturnsPanel.tsx` | Table (negative amount in red) + drawer (credit-note + stock entry links); create from POSTED invoice. |
| 22 | `ArAgingPanel.tsx` | As ApAging. |

### Manufacturing (`src/features/manufacturing/`)

| # | File | Treatment |
|---|------|-----------|
| 23 | `ManufacturingPage.tsx` | Tabs + `PageHeader`. |
| 24 | `WorkOrdersPanel.tsx` | Table (state-machine StatusBadge) + drawer (components: planned/consumed/value); conditional `StateButton`s (Release/Issue/Complete/Cancel) each with an action modal; new-WO modal (finished item + BOM-by-parent + qty). |
| 25 | `BomsPanel.tsx` | Table + drawer (component lines: item/qty/scrap%); modal with dynamic component lines; `aria-label` delete. |
| 26 | `ReorderReportPanel.tsx` | Read-only table (highlight below reorder point) or top-N `StatTile`s. |

### Inventory (`src/features/inventory/`)

| # | File | Treatment |
|---|------|-----------|
| 27 | `InventoryPage.tsx` | Tabs + `PageHeader`. |
| 28 | Overview panel | On-hand lookup `EntitySelect` → 3 `StatTile`s (qty/avg cost/value); reconciliation `CardWrapper` table; loaders. |
| 29 | `AdjustmentsPanel.tsx` | Create `Button` (WAREHOUSE role); table + entry link; modal (item, STOCK-only location, signed qtyDelta, unitCost, reason). |

### Reporting (`src/features/reporting/`)

| # | File | Treatment |
|---|------|-----------|
| 30 | `ReportsPage.tsx` | Tabs + `PageHeader` with as-of `DateInput` (right). |
| 31 | `ReconciliationHero.tsx` | Bespoke hero: 48px icon (terracotta healthy / red not) + title + status badge; subledgers table + clearing-account cards; warm border on healthy; loader. Used on Dashboard. |
| 32 | `TrialBalancePanel.tsx` | Table (class badge, debit/credit right) → clickable row opens GL `DetailDrawer`; bold totals row. |
| 33 | `IncomeStatementPanel.tsx` | `StatementSection` hierarchy (Revenue/COGS/Gross/Expenses/Net); terracotta section headers; right-aligned `MoneyText`; highlighted Net Income. |
| 34 | `BalanceSheetPanel.tsx` | `StatementSection` Assets vs Liabilities+Equity; balance-validation badge (green check). |

### Ledger (`src/features/ledger/`)

| # | File | Treatment |
|---|------|-----------|
| 35 | `LedgerPage.tsx` | Tabs + `PageHeader`. |
| 36 | `ManualEntryPanel.tsx` | Create `Button` (ACCOUNTANT); modal with dynamic debit/credit lines + real-time balance check (green/red, submit disabled until balanced & ≥2 lines); table + read-only `DetailDrawer`; `aria-label` delete. |
| 37 | `ReversalPanel.tsx` | Entry# input + Load; drawer preview; Reverse `StateButton` (danger) → `ConfirmationModal`; disabled when not found / already reversed / non-manual / no permission. |
| 38 | `FiscalPeriodsPanel.tsx` | Table (year/period/status badges + dates); Close (warning) / Reopen (danger) via `ConfirmationModal`; Year-End Close section (danger, disabled if all LOCKED) showing Net Income + entry# + locked count. |

### Audit (`src/features/audit/`)

| # | File | Treatment |
|---|------|-----------|
| 39 | `AuditPage.tsx` | Larger title/subtitle; filters (event-type Select clearable, actor TextInput debounced 300ms, total-count badge); table (event-type colored badges); pagination; `EmptyState`; loader. Read-only (ADMIN). |

### Print routes (`src/features/print/` — new, pure frontend)

| # | File | Treatment |
|---|------|-----------|
| 40 | `PrintLayout.tsx` | A4 shell; `PrintHeader` (logo + title); on-screen print/return buttons hidden on print; auto `window.print()` on ready; `@page A4` margins. |
| 41 | `PrintSalesInvoice.tsx` | A4 invoice (header, lines incl. COGS, goods/VAT/gross, signature block). |
| 42 | `PrintPurchaseOrder.tsx` | A4 PO (header, lines, terms). |
| 43 | `PrintDelivery.tsx` | A4 packing slip (header, shipped qty, SKU). |
| 44 | `PrintTrialBalance.tsx` | Landscape A4 TB (code/name/debit/credit + totals). |

New `frontend/src/styles/print.css`:

```css
@media print {
  @page { size: A4; margin: 0.5in; }
  body { background: white; }
  .print-hidden { display: none !important; }
  table thead { display: table-header-group; }
  tbody tr { page-break-inside: avoid; }
}
```

---

## 10. Program phasing & PR breakdown

Every PR must stay green on `npm run build` (`tsc -b && vite build`) **and** `npm run test:run`. Touch **no backend** (no schema regen, no `mvn verify`).

### Phase 1 — Pre-redesign hardening (bundle + concurrency ITs)
Sibling goals that de-risk the redesign and keep the demo trustworthy:
- Bundle slimming groundwork (audit/route-split as feasible) so the webfont add is net-neutral.
- Backend concurrency integration tests (existing sibling effort) — independent of this redesign; sequence so the redesign branches off a stable main.

### Phase 2 — The redesign, as N batched PRs
Ordered so the two foundation PRs re-skin the whole app first, then targeted polish:

- **PR 2.1 — Theme foundation (no per-panel rollout).** Terracotta ramp + warm-gray scale + warm dark palette + radii/shadows/spacing/type + CSS-var surfaces; self-hosted Plus Jakarta Sans (2 weights, preload, swap); **color-scheme toggle** in `AppLayout` header. Highest-leverage, lowest-risk — concentrated in `theme.ts` + `index.css` + `main.tsx` + `AppLayout.tsx` + fonts. Re-skins everything via inheritance.
- **PR 2.2 — `theme.components` overrides + shared components.** All Mantine `Component.extend` overrides; new shared components (§4.3); enhance `StatusBadge`/`Money`/`PageHeader`/`EntitySelect`. **Isolate here** — this is where strict-TS typing pain concentrates; keep it off the rollout critical path.
- **PR 2.3 — Logo + favicon.** Replace default Vite favicon (`favicon-light/dark.svg`, optional PNG/apple-touch); header logo mark in `AppLayout`. Tiny, self-contained, high portfolio signal.
- **PRs 2.4–2.8 — Per-module polish (one module per PR):** purchasing / sales / manufacturing / masterdata+inventory / reporting+ledger. Only heroes, dashboards, and dense forms get hand-touched (adopt `DataTable`/`DetailDrawer`/`StatTile`/`StateButton`/`StatementSection`/`AmountAllocationTable`); trivial tables inherit and are skipped. Print routes land with their owning module (invoice/PO/delivery → sales/purchasing; TB → reporting). **Fold the a11y `aria-label` fixes into each module PR** that touches the offending file.
- **PR 2.9 — Onboarding tour.** Hand-rolled, opt-in, behind localStorage; new `src/onboarding/*` + `data-onboarding` attrs + `modules/onboarding.ts` strings + Help/restart menu item.
- **PR 2.10 — a11y sweep (only if not already folded in).** Remaining icon-button labels, Modal/Drawer focus-trap audit, keyboard pass, optional `axe-playwright`/Lighthouse-CI (report-only). If 2.4–2.8 already folded a11y in, skip or shrink this.

### Phase 3 — Docs + screenshots
Update `PROGRESS.md` and frontend `README.md` (palette, font stack, dark toggle, onboarding). Capture light+dark screenshots of key pages from the Oracle/Docker demo for the repo.

### Phase 4 — Résumé repo
Curate highlights (theme system, bespoke component layer, hand-rolled tour, accessibility, bilingual i18n) into the résumé/portfolio repository. **Résumé repo location is TBD — needs owner input** before this phase starts.

---

## 11. Verification

The sandbox **cannot run a loopback dev server** (no real Tomcat/Vite dev server). Per-PR gate:

1. `npm run build` → `tsc -b && vite build` passes (strict TS, both schemes compile, all i18n keys paired in `en` + `zh-TW`).
2. `npm run test:run` (Vitest) + `npm run test:types` pass. Add snapshot tests of `StatusBadge`/`Money` under `colorScheme` `light` and `dark` to guard dark-mode parity.
3. **Visual review on the Oracle/Docker demo** (`ssh oracle`, ARM64) — click-test light/dark, onboarding first-run, print routes, keyboard nav. This is the only place real rendering is verified; do not claim visual verification performed in the sandbox.

Per-PR checklist: build green; tests green; i18n keys paired; new components under `src/components/` or `src/features/<module>/`; icon-only buttons have `aria-label`; Modals/Drawers focus-trapped; empty/loading states present; responsive at 375px and 1920px.

---

## 12. Out of scope / cut

- **CUT — from-scratch component library.** `theme.components` overrides + the §4.3 shared components are the ceiling. No bespoke primitives.
- **CUT — pixel-by-pixel hand-edit of trivially identical panels.** Aging panels, print pages, `StatementSection`-driven statements, `ReportsPage`, most masterdata tables re-skin for free (zero hardcoded colors). Hand-touch heroes/dashboards/dense forms only.
- **CUT — tour-library dependency** (`reactour`/`shepherd`/`driver.js`). Hand-roll (§6).
- **CUT — multi-MB CJK webfont.** System CJK stack only.
- **DEFER — multi-weight webfont** (ship 2 weights), animated/elaborate tour (3–5 steps on the key flows, then stop), per-panel micro-polish on low-traffic screens.
- **DEFER — automated a11y CI** (axe/Lighthouse) — land report-only after dark mode; baseline, don't block `main`.
- **OUT — any backend change.** No `schema.d.ts` regen, no `OpenApiSpecIT`, no `mvn verify`, no endpoint "tidying". 100% frontend; flag any backend temptation as scope creep.

### Key files referenced

- `frontend/src/theme.ts` (9-line stub → full theme)
- `frontend/src/main.tsx` (provider tree, `defaultColorScheme="auto"`)
- `frontend/src/components/AppLayout.tsx` (header toggle + logo, nav grouping, lines ~65–77)
- `frontend/src/components/{PageHeader,StatusBadge,Money,EntitySelect}.tsx`
- `frontend/src/i18n/messages/{en.ts,zh-TW.ts,modules/*.ts}` (paired-string burden)
- `frontend/index.html` (default favicon to replace; font preload)
- `frontend/src/features/**` (41 panels; 4 icon-only ActionIcon `aria-label` fixes)
