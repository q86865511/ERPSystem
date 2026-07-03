import { Badge } from '@mantine/core';
import { useI18n } from '../i18n';
import type { TranslationKey } from '../i18n';
import { SealBadge } from './SealBadge';
import type { SealVariant } from './SealBadge';

/**
 * Document review/posting statuses that render as a stamp (design.md §6) instead of a plain light Badge.
 * Everything not listed here keeps the existing Badge path below (incl. the teal POSITIVE_STYLES fix and
 * the brand-colored active states) — so the 16 feature panels need zero changes; the split is internal.
 */
const SEAL_VARIANT: Record<string, SealVariant> = {
  APPROVED: 'stamp', // 朱印:圓形雙框朱紅章
  POSTED: 'ink', // 墨印:ink filled 圓角矩形
  CLOSED: 'ink',
  PENDING: 'pending', // 待審灰框 chip(還沒蓋)
  SUBMITTED: 'pending',
  DRAFT: 'draft', // subtle 灰(維持現行 gray light Badge 外觀)
};

// The status→color map for the plain light-Badge path. Entries for the six SEAL_VARIANT statuses above
// (DRAFT/POSTED/CLOSED/PENDING/APPROVED/SUBMITTED) are now inert — they are intercepted before this map —
// but are kept as documentation of their original semantic color.
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
  const text = label === key ? status.replaceAll('_', ' ') : label;

  // Stamp-language statuses (design.md §6) render as a SealBadge; everything else stays on the Badge path.
  const sealVariant = SEAL_VARIANT[status];
  if (sealVariant) {
    return <SealBadge status={status} variant={sealVariant} label={text} />;
  }

  const color = COLOR[status] ?? 'gray';
  return (
    <Badge color={color} variant="light" styles={color === 'teal' ? POSITIVE_STYLES : undefined}>
      {text}
    </Badge>
  );
}
