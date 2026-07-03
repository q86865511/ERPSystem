import { Badge } from '@mantine/core';
import { useEffect, useRef, useState } from 'react';
import classes from './SealBadge.module.css';

/**
 * The Ink Ledger signature element (design.md §6): a document's review/posting status rendered as a
 * stamp. StatusBadge routes only the four "stamp-worthy" statuses here; everything else keeps the plain
 * light <Badge> path. Four shapes:
 *   - `stamp`   朱印 — vermillion circular double-frame, rotated -8deg (APPROVED)
 *   - `ink`     墨印 — ink filled rounded-rect, not rotated (POSTED / CLOSED)
 *   - `pending` 待審 — grey outline chip, "not stamped yet" (PENDING / SUBMITTED)
 *   - `draft`   草稿 — subtle grey (DRAFT)
 *
 * The vermillion stamp plays a one-off "drop" animation *only* when a mounted badge transitions into it
 * (APPROVED arriving after a non-approved value) — the first render of an already-approved row never
 * animates, so scrolling a list of approved docs is silent. Reduced-motion is handled purely in CSS.
 */
export type SealVariant = 'stamp' | 'ink' | 'pending' | 'draft';

export function SealBadge({ status, variant, label }: { status: string; variant: SealVariant; label: string }) {
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

  if (variant === 'stamp') {
    return (
      <span
        className={`${classes.serif} ${classes.stamp}${dropping ? ` ${classes.stampDrop}` : ''}`}
        data-seal-variant="stamp"
      >
        {label}
      </span>
    );
  }
  if (variant === 'ink') {
    return (
      <span className={`${classes.serif} ${classes.inkStamp}`} data-seal-variant="ink">
        {label}
      </span>
    );
  }
  if (variant === 'pending') {
    return (
      <span className={classes.pendingChip} data-seal-variant="pending">
        {label}
      </span>
    );
  }
  // draft — "subtle 灰" (design.md §6): keep the existing gray light Badge look exactly, just tagged so
  // callers/tests can see it flowed through the seal router. No stamp language, no custom chrome.
  return (
    <Badge color="gray" variant="light" data-seal-variant="draft">
      {label}
    </Badge>
  );
}
