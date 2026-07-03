/**
 * Chart palette for the Ink Ledger dashboards. Categorical series follow a fixed order (never cycled
 * as a rainbow); the aging ramp encodes severity (not-due → 90+). `chartSeries` stays CSS var references
 * (defined in index.css) so the ink hue (--erp-chart-1) can be swapped for a lighter tint in dark mode —
 * ink-6 #2f6559 is only ~2.3:1 on the #1d2724 dark card surface. The rest of the ramp stays mid-tone and
 * doesn't need a dark override. recharts/@mantine/charts only ever pass these strings through to SVG
 * fill/stroke, so a CSS var resolves fine — nothing here does string/alpha math on the color values.
 * Display values still go through `formatMoney` — these numbers are for chart sizing only, not accounting.
 *
 * `agingColors`/`categoryColors` are raw hex (not CSS vars) picked from the design.md §2.6 eight-color
 * chart set only — never the seal ramp (§2.2's usage rule reserves seal for the stamp/danger language).
 */
export const chartSeries = [
  'var(--erp-chart-1)',
  'var(--erp-chart-2)',
  'var(--erp-chart-3)',
  'var(--erp-chart-4)',
  'var(--erp-chart-5)',
  'var(--erp-chart-6)',
  'var(--erp-chart-7)',
  'var(--erp-chart-8)',
] as const;

/**
 * AR/AP aging severity, not-due → 90+ (design.md §2.6 hex, five-step ramp agreed for this migration):
 * not-due 墨青 → 1-30 苔綠 #8A8B5C → 31-60 藤黃 #C99235 → 61-90 牡丹 #A96C8E → 90+ 磚朱 #A8574A.
 * The ink step goes through --erp-chart-1 (NOT raw #2F6559) so it picks up the dark-mode #5FA396
 * override — raw ink is only ~2.3:1 on the dark card; the mid-tone rest needs no override.
 */
export const agingColors = ['var(--erp-chart-1)', '#8A8B5C', '#C99235', '#A96C8E', '#A8574A'] as const;

/**
 * Inventory category (raw / WIP / finished / other), design.md §2.6: raw 墨青 via --erp-chart-1 (dark-
 * aware, see agingColors note), WIP 青瓷綠 #5E9E8F, finished 藤黃 #C99235, other 暖石灰 gray-4 #A6A69C
 * (design.md §2.3, not a chart color).
 */
export const categoryColors = ['var(--erp-chart-1)', '#5E9E8F', '#C99235', '#A6A69C'] as const;

/**
 * Inventory heat-treemap tile fill by stock status (design.md §2.6 fixed hex — same OK/LOW/OUT semantic
 * as the reorder Badge/Progress colors, but treemap tiles are SVG rects, not Mantine components, so they
 * need raw hex rather than the `--erp-*` CSS vars): OK 墨青 #2F6559, LOW 藤黃 #C99235, OUT 磚朱 #A8574A.
 */
export const treemapFill = { OK: '#2F6559', LOW: '#C99235', OUT: '#A8574A' } as const;

/**
 * Per-status tile label color, chosen for >=4.5:1 contrast against `treemapFill`. White reads fine on the
 * dark OK/OUT fills, but white-on-藤黃(LOW) is only ~2.75:1 — LOW gets a deep-ink label instead.
 */
export const treemapLabelColor = { OK: '#FFFFFF', LOW: '#1C2321', OUT: '#FFFFFF' } as const;

/** Parse a backend money string ("12345.67") into a number for chart sizing only. */
export function moneyToNumber(v: string | number | null | undefined): number {
  if (v == null || v === '') return 0;
  const n = Number(String(v));
  return Number.isFinite(n) ? n : 0;
}

/** Compact axis label: 1.2M / 340K / 56 — keeps chart tick labels narrow (tooltips use exact formatMoney). */
export function compactNumber(v: number): string {
  if (!Number.isFinite(v)) return '';
  const a = Math.abs(v);
  if (a >= 1e6) return `${(v / 1e6).toFixed(a >= 1e7 ? 0 : 1)}M`;
  if (a >= 1e3) return `${(v / 1e3).toFixed(0)}K`;
  return String(Math.round(v));
}
