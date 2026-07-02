import { Badge } from '@mantine/core';
import { useI18n } from '../i18n';
import type { TranslationKey } from '../i18n';

const COLOR: Record<string, string> = {
  DRAFT: 'gray',
  // "Active / in-our-hands" primary states map to the brand brand (was 'blue' pre-2.2).
  CONFIRMED: 'brand',
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
  IN_PROGRESS: 'brand',
  RELEASED: 'brand',
  DONE: 'teal',
  // bill match status
  MATCHED: 'teal',
  PARTIAL: 'yellow',
  UNMATCHED: 'red',
  // fiscal period status
  OPEN: 'teal',
  LOCKED: 'red',
  // HR leave / timesheet lifecycle
  PENDING: 'yellow',
  APPROVED: 'teal',
  REJECTED: 'red',
  SUBMITTED: 'brand',
  // HR attendance
  PRESENT: 'teal',
  ABSENT: 'red',
  LATE: 'yellow',
  LEAVE: 'grape',
  REMOTE: 'brand',
};

/** Teal ("done/positive") statuses reuse the ERP-015 semantic tokens, same fix as LiveBadge — the stock
    teal light variant measures 4.32:1 (below AA); the other color families already clear the axe gate. */
const POSITIVE_STYLES = {
  root: { backgroundColor: 'var(--erp-positive-bg)' },
  label: { color: 'var(--erp-positive-text)' },
};

export function StatusBadge({ status }: { status: string | undefined }) {
  const { t } = useI18n();
  if (!status) return null;
  // status.* mirrors the backend enum codes; fall back to the prettified code for any unmapped token.
  const key = `status.${status}` as TranslationKey;
  const label = t(key);
  const color = COLOR[status] ?? 'gray';
  return (
    <Badge color={color} variant="light" styles={color === 'teal' ? POSITIVE_STYLES : undefined}>
      {label === key ? status.replaceAll('_', ' ') : label}
    </Badge>
  );
}
