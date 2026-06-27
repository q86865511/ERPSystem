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

export function MoneyText({ value }: { value: string | number | null | undefined }) {
  return (
    <Text component="span" ff="monospace">
      {formatMoney(value)}
    </Text>
  );
}
