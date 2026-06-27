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

export function MoneyText({ value }: { value: string | number | null | undefined }) {
  return (
    <Text component="span" ff="monospace">
      {formatMoney(value)}
    </Text>
  );
}
