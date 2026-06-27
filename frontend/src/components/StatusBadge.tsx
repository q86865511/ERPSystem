import { Badge } from '@mantine/core';

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
  if (!status) return null;
  return (
    <Badge color={COLOR[status] ?? 'gray'} variant="light">
      {status.replaceAll('_', ' ')}
    </Badge>
  );
}
