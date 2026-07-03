import { Text } from '@mantine/core';

/**
 * Formats a backend money/decimal value — which arrives as a STRING (never a float) — for display, with
 * thousands grouping and two decimals, using string operations so no precision is lost.
 */
export function formatMoney(value: string | number | null | undefined): string {
  if (value == null || value === '') return '—';
  const raw = String(value).trim();
  const negative = raw.startsWith('-');
  const abs = negative ? raw.slice(1) : raw;
  const [intPart = '0', decPart = ''] = abs.split('.');
  const dec2 = (decPart + '00').slice(0, 2);
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negative ? '-' : ''}${grouped}.${dec2}`;
}

/**
 * Exact sum of money strings at scale 4 using BigInt — never floats — so the total equals the backend's
 * BigDecimal sum (e.g. for a payment amount that must equal its allocation amounts).
 */
export function sumMoney(values: Array<string | undefined>): string {
  let total = 0n;
  for (const raw of values) {
    const v = (raw ?? '').trim();
    if (!v) continue;
    const negative = v.startsWith('-');
    const abs = negative ? v.slice(1) : v;
    const [intPart = '0', fracPart = ''] = abs.split('.');
    const frac4 = (fracPart + '0000').slice(0, 4);
    let units = BigInt(intPart || '0') * 10000n + BigInt(frac4 || '0');
    if (negative) units = -units;
    total += units;
  }
  const negative = total < 0n;
  const abs = negative ? -total : total;
  const intPart = abs / 10000n;
  const fracPart = (abs % 10000n).toString().padStart(4, '0');
  return `${negative ? '-' : ''}${intPart}.${fracPart}`;
}

/**
 * Ink Ledger drops the monospace font for money (design.md §3): the body sans, Plus Jakarta Sans, ships
 * an OpenType `tnum` (tabular figures) feature — verified via fontTools against the self-hosted woff2,
 * every digit's `.tf` variant is a uniform 600 units wide vs. 371–732 for the proportional default — so
 * `font-variant-numeric: tabular-nums` alone gives equal-width digits without switching type families.
 * Monospace is reserved for unit/reference codes only (design.md §3), not amounts.
 */
export function MoneyText({ value }: { value: string | number | null | undefined }) {
  return (
    <Text component="span" style={{ fontVariantNumeric: 'tabular-nums' }}>
      {formatMoney(value)}
    </Text>
  );
}
