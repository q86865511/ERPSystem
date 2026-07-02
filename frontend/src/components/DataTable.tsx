import { ActionIcon, Skeleton, Table } from '@mantine/core';
import type { ReactNode } from 'react';
import { useI18n } from '../i18n';
import { EmptyState } from './EmptyState';

export interface DataTableColumn<T> {
  key: string;
  label: ReactNode;
  align?: 'left' | 'center' | 'right';
  width?: string | number;
  /** Custom cell renderer; without it the cell shows `row[key]` (must be a primitive). */
  render?: (row: T) => ReactNode;
}

/**
 * Generic list table. Declare `columns` + `rows` and get the warm-skinned striped/hover table with
 * built-in loading and empty states. When `onRowClick` is set the whole row is clickable (cursor + a
 * trailing chevron), and the chevron is a real focusable button (keyboard-accessible) labelled by
 * `rowActionLabel` — the standard "row → detail drawer" pattern across the app.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  isLoading,
  isEmpty,
  emptyMessage,
  onRowClick,
  rowActionLabel,
  minWidth,
}: {
  columns: DataTableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  isLoading?: boolean;
  isEmpty?: boolean;
  emptyMessage: string;
  onRowClick?: (row: T) => void;
  rowActionLabel?: string;
  minWidth?: number;
}) {
  const { t } = useI18n();

  if (isLoading) {
    // Column-shaped skeleton (header + 5 rows) so the layout doesn't jump when data arrives (ERP-005).
    const skeleton = (
      <Table>
        <Table.Thead>
          <Table.Tr>
            {columns.map((col) => (
              <Table.Th key={col.key} ta={col.align} w={col.width}>
                <Skeleton height={12} width="60%" />
              </Table.Th>
            ))}
            {onRowClick && <Table.Th w={48} aria-hidden />}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {Array.from({ length: 5 }).map((_, r) => (
            <Table.Tr key={r}>
              {columns.map((col) => (
                <Table.Td key={col.key} ta={col.align}>
                  <Skeleton height={14} />
                </Table.Td>
              ))}
              {onRowClick && <Table.Td w={48} aria-hidden />}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    );
    return minWidth ? <Table.ScrollContainer minWidth={minWidth}>{skeleton}</Table.ScrollContainer> : skeleton;
  }
  if (isEmpty || rows.length === 0) {
    return <EmptyState message={emptyMessage} />;
  }

  const cellContent = (row: T, col: DataTableColumn<T>): ReactNode =>
    col.render ? col.render(row) : ((row as Record<string, unknown>)[col.key] as ReactNode);

  const table = (
    <Table striped highlightOnHover>
      <Table.Thead>
        <Table.Tr>
          {columns.map((col) => (
            <Table.Th key={col.key} ta={col.align} w={col.width}>
              {col.label}
            </Table.Th>
          ))}
          {onRowClick && <Table.Th w={48} aria-hidden />}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {rows.map((row) => (
          <Table.Tr
            key={rowKey(row)}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            style={onRowClick ? { cursor: 'pointer' } : undefined}
          >
            {columns.map((col) => (
              <Table.Td key={col.key} ta={col.align}>
                {cellContent(row, col)}
              </Table.Td>
            ))}
            {onRowClick && (
              <Table.Td ta="right">
                <ActionIcon
                  variant="subtle"
                  color="gray"
                  aria-label={rowActionLabel ?? t('common.view')}
                  onClick={(e) => {
                    e.stopPropagation();
                    onRowClick(row);
                  }}
                >
                  <span aria-hidden>›</span>
                </ActionIcon>
              </Table.Td>
            )}
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );

  return minWidth ? <Table.ScrollContainer minWidth={minWidth}>{table}</Table.ScrollContainer> : table;
}
