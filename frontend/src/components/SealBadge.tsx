import { Badge } from '@mantine/core';
import { useEffect, useRef, useState } from 'react';
import classes from './SealBadge.module.css';

/**
 * The Ink Ledger signature element (design.md §6): a document's review/posting status rendered as a
 * stamp. StatusBadge routes only the four "stamp-worthy" statuses here; everything else keeps the plain
 * light <Badge> path. Four shapes:
 *   - `stamp`   朱印 — vermillion double-frame, rotated -8deg (APPROVED)
 *   - `ink`     墨印 — ink filled rounded-rect, not rotated (POSTED / CLOSED)
 *   - `pending` 待審 — grey outline chip, "not stamped yet" (PENDING / SUBMITTED)
 *   - `draft`   草稿 — subtle grey (DRAFT)
 *
 * Two sizes (design.md §6): `md` (full 40px stamp) for a detail page's status area, `sm` (~28px compact
 * stamp) for table cells / list rows so a seal never makes its row taller than its neighbours. Default is
 * `sm` — the many list usages need no change; only the ~dozen detail status-area sites pass `size="md"`.
 *
 * The 朱印 outline is a circle for short CJK labels (≤3 中日韓 chars, e.g. 已核准) and a rounded rectangle
 * for anything longer (e.g. English "Approved"), which the circle can't hold on one line — same seal
 * color, same -8deg rotation, same drop animation, width auto from the text (design.md §6).
 *
 * The vermillion stamp plays a one-off "drop" animation *only* when a mounted badge transitions into it
 * (APPROVED arriving after a non-approved value) — the first render of an already-approved row never
 * animates, so scrolling a list of approved docs is silent. Reduced-motion is handled purely in CSS.
 */
export type SealVariant = 'stamp' | 'ink' | 'pending' | 'draft';
export type SealSize = 'sm' | 'md';

// A "short CJK label" (≤3 中日韓 chars) fits the circular stamp; anything else uses the rounded-rect stamp.
// The range covers CJK Unified Ideographs + the common Ext-A block; kana/hangul aren't expected here but
// the intent is "CJK script", so treating this as CJK-only (Latin → rectangle) matches the adjudication.
const CJK = /[㐀-鿿]/;
function isShortCjk(label: string): boolean {
  const chars = [...label];
  return chars.length <= 3 && chars.every((c) => CJK.test(c));
}

export function SealBadge({
  status,
  variant,
  label,
  size = 'sm',
}: {
  status: string;
  variant: SealVariant;
  label: string;
  size?: SealSize;
}) {
  // Track the previous status across renders to fire the stamp-drop only on a genuine transition into the
  // approved stamp. `undefined` on the very first render means "just mounted" → no animation.
  const prev = useRef<string | undefined>(undefined);
  const [dropping, setDropping] = useState(false);

  useEffect(() => {
    const becameStamp = prev.current !== undefined && prev.current !== status && variant === 'stamp';
    if (becameStamp) {
      setDropping(true);
      const id = window.setTimeout(() => setDropping(false), 180);
      prev.current = status;
      return () => window.clearTimeout(id);
    }
    prev.current = status;
    return undefined;
  }, [status, variant]);

  const sizeClass = size === 'md' ? classes.md : classes.sm;

  if (variant === 'stamp') {
    // Circle for short CJK, rounded rectangle otherwise (design.md §6). `data-seal-shape` lets tests
    // assert which outline was chosen without depending on the hashed CSS-module class names.
    const circle = isShortCjk(label);
    const shapeClass = circle ? classes.stamp : classes.stampRect;
    return (
      <span
        className={`${classes.serif} ${sizeClass} ${shapeClass}${dropping ? ` ${classes.stampDrop}` : ''}`}
        data-seal-variant="stamp"
        data-seal-shape={circle ? 'circle' : 'rect'}
        data-seal-size={size}
      >
        {label}
      </span>
    );
  }
  if (variant === 'ink') {
    return (
      <span className={`${classes.serif} ${sizeClass} ${classes.inkStamp}`} data-seal-variant="ink" data-seal-size={size}>
        {label}
      </span>
    );
  }
  if (variant === 'pending') {
    return (
      <span className={`${sizeClass} ${classes.pendingChip}`} data-seal-variant="pending" data-seal-size={size}>
        {label}
      </span>
    );
  }
  // draft — "subtle 灰" (design.md §6): keep the existing gray light Badge look exactly, just tagged so
  // callers/tests can see it flowed through the seal router. No stamp language, no custom chrome. The
  // gray light Badge already reads well at either scale, so size doesn't alter it.
  return (
    <Badge color="gray" variant="light" data-seal-variant="draft">
      {label}
    </Badge>
  );
}
