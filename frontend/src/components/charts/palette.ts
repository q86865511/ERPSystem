/**
 * Chart palette for the Blue Enterprise dashboards. Categorical series follow a fixed order (never cycled
 * as a rainbow); the aging ramp encodes severity (not-due → 90+). Raw hex so recharts renders identically
 * in light and dark (all values are mid-tones readable on both surfaces). Display values still go through
 * `formatMoney` — these numbers are for chart sizing only, not accounting.
 */
export const chartSeries = [
  '#2563eb',
  '#0ea5e9',
  '#14b8a6',
  '#f59e0b',
  '#8b5cf6',
  '#ef4444',
  '#ec4899',
  '#f97316',
] as const;

/** AR/AP aging severity: not-due (blue, good) → 1-30, 31-60, 61-90, 90+ (red, critical). */
export const agingColors = ['#2563eb', '#60a5fa', '#f59e0b', '#f97316', '#ef4444'] as const;

/** Inventory category (raw / WIP / finished / other). */
export const categoryColors = ['#2563eb', '#14b8a6', '#f59e0b', '#94a3b8'] as const;

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
