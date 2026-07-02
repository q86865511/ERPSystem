/**
 * Chart palette for the Blue Enterprise dashboards. Categorical series follow a fixed order (never cycled
 * as a rainbow); the aging ramp encodes severity (not-due → 90+). Values are CSS var references
 * (defined in index.css) rather than raw hex so the brand blue (--erp-chart-1) can be swapped for a
 * lighter tint in dark mode — #2563eb was only ~3.0:1 on the #242424 dark card surface. The rest of the
 * ramp stays mid-tone and doesn't need a dark override. recharts/@mantine/charts only ever pass these
 * strings through to SVG fill/stroke, so a CSS var resolves fine — nothing here does string/alpha math
 * on the color values. Display values still go through `formatMoney` — these numbers are for chart
 * sizing only, not accounting.
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

/** AR/AP aging severity: not-due (blue, good) → 1-30, 31-60, 61-90, 90+ (red, critical). */
export const agingColors = ['var(--erp-chart-1)', '#60a5fa', '#f59e0b', '#f97316', '#ef4444'] as const;

/** Inventory category (raw / WIP / finished / other). */
export const categoryColors = ['var(--erp-chart-1)', '#14b8a6', '#f59e0b', '#94a3b8'] as const;

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
