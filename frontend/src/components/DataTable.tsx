import { ActionIcon, Group, Pagination, Skeleton, Stack, Table, Text, TextInput, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconChevronUp, IconSearch, IconSelector } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
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
  /** Enables click-to-sort on this column's header (sorts by the raw `row[key]` value). */
  sortable?: boolean;
  /** Text to match this column against when searching — needed for render-only columns whose `key` isn't a
   *  raw field (e.g. a "customer" column that renders a looked-up partner name). Falls back to `row[key]`. */
  searchValue?: (row: T) => string | number | null | undefined;
}

const rawValue = <T,>(row: T, key: string): unknown => (row as Record<string, unknown>)[key];

/**
 * Generic list table. Declare `columns` + `rows` and get a hover-highlighted table with built-in loading
 * (column-shaped skeleton) and empty states. Sorting (opt-in per `column.sortable`), an optional search box
 * (`searchable`) and client-side pagination (automatic once rows exceed `pageSize`) are handled internally —
 * callers keep passing the full `rows` array unchanged. When `onRowClick` is set the whole row is clickable
 * plus a trailing focusable chevron (the standard "row → detail drawer" pattern).
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
  searchable,
  pageSize = 25,
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
  /** Shows a search box that filters rows across their raw cell values. */
  searchable?: boolean;
  /** Rows per page; pagination controls appear only when the (filtered) rows exceed this. Default 25. */
  pageSize?: number;
}) {
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<{ key: string; dir: 'asc' | 'desc' } | null>(null);
  const [page, setPage] = useState(1);

  const filtered = useMemo(() => {
    if (!searchable || !query.trim()) return rows;
    const q = query.trim().toLowerCase();
    return rows.filter((row) =>
      columns.some((c) => {
        const v = c.searchValue ? c.searchValue(row) : rawValue(row, c.key);
        return v != null && String(v).toLowerCase().includes(q);
      }),
    );
  }, [rows, columns, query, searchable]);

  const sorted = useMemo(() => {
    if (!sort) return filtered;
    const arr = [...filtered];
    arr.sort((a, b) => {
      const av = rawValue(a, sort.key);
      const bv = rawValue(b, sort.key);
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;
      const cmp =
        typeof av === 'number' && typeof bv === 'number'
          ? av - bv
          : String(av).localeCompare(String(bv), undefined, { numeric: true });
      return sort.dir === 'asc' ? cmp : -cmp;
    });
    return arr;
  }, [filtered, sort]);

  const total = sorted.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.min(page, pageCount);
  const paged = total > pageSize ? sorted.slice((current - 1) * pageSize, current * pageSize) : sorted;

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

  const toggleSort = (key: string) => {
    setPage(1);
    setSort((s) => {
      if (!s || s.key !== key) return { key, dir: 'asc' };
      if (s.dir === 'asc') return { key, dir: 'desc' };
      return null; // asc → desc → unsorted
    });
  };

  const cellContent = (row: T, col: DataTableColumn<T>): ReactNode =>
    col.render ? col.render(row) : (rawValue(row, col.key) as ReactNode);

  const table = (
    <Table highlightOnHover withRowBorders>
      <Table.Thead>
        <Table.Tr>
          {columns.map((col) => (
            <Table.Th key={col.key} ta={col.align} w={col.width}>
              {col.sortable ? (
                <UnstyledButton
                  onClick={() => toggleSort(col.key)}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 4, font: 'inherit', color: 'inherit' }}
                >
                  {col.label}
                  {sort?.key === col.key ? (
                    sort.dir === 'asc' ? <IconChevronUp size={14} /> : <IconChevronDown size={14} />
                  ) : (
                    <IconSelector size={14} style={{ opacity: 0.4 }} />
                  )}
                </UnstyledButton>
              ) : (
                col.label
              )}
            </Table.Th>
          ))}
          {onRowClick && <Table.Th w={48} aria-hidden />}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {paged.map((row) => (
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

  const body = minWidth ? <Table.ScrollContainer minWidth={minWidth}>{table}</Table.ScrollContainer> : table;

  return (
    <Stack gap="sm">
      {searchable && (
        <Group justify="space-between" wrap="wrap" gap="sm">
          <TextInput
            value={query}
            onChange={(e) => {
              setQuery(e.currentTarget.value);
              setPage(1);
            }}
            placeholder={t('common.search')}
            leftSection={<IconSearch size={16} />}
            w={260}
            aria-label={t('common.search')}
          />
          <Text size="sm" c="dimmed">
            {t('common.rowsTotal', { total })}
          </Text>
        </Group>
      )}
      {total === 0 ? <EmptyState message={t('common.noResults')} /> : body}
      {total > pageSize && (
        <Group justify="space-between" wrap="wrap" gap="sm">
          <Text size="sm" c="dimmed">
            {t('common.showingRange', {
              from: (current - 1) * pageSize + 1,
              to: Math.min(current * pageSize, total),
              total,
            })}
          </Text>
          <Pagination value={current} onChange={setPage} total={pageCount} size="sm" />
        </Group>
      )}
    </Stack>
  );
}
