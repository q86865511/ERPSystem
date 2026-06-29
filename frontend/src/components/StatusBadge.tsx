import { Badge } from '@mantine/core';
import { useI18n } from '../i18n';
import type { TranslationKey } from '../i18n';

const COLOR: Record<string, string> = {
  DRAFT: 'gray',
  CONFIRMED: 'blue',
  PARTIALLY_RECEIVED: 'yellow',
  RECEIVED: 'teal',
  PARTIALLY_SHIPPED: 'yellow',
  SHIPPED: 'teal',
  PARTIALLY_PAID: 'yellow',
  PAID: 'teal',
  POSTED: 'teal',
  RETURNED: 'grape',
  CLOSED: 'teal',
  CANCELLED: 'red',
  IN_PROGRESS: 'blue',
  RELEASED: 'blue',
  DONE: 'teal',
  // bill match status
  MATCHED: 'teal',
  PARTIAL: 'yellow',
  UNMATCHED: 'red',
  // fiscal period status
  OPEN: 'teal',
  LOCKED: 'red',
};

export function StatusBadge({ status }: { status: string | undefined }) {
  const { t } = useI18n();
  if (!status) return null;
  // status.* mirrors the backend enum codes; fall back to the prettified code for any unmapped token.
  const key = `status.${status}` as TranslationKey;
  const label = t(key);
  return (
    <Badge color={COLOR[status] ?? 'gray'} variant="light">
      {label === key ? status.replaceAll('_', ' ') : label}
    </Badge>
  );
}
