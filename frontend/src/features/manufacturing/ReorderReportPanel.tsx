import { Text } from '@mantine/core';
import { DataTable } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import { useReorderReport } from './api';

export function ReorderReportPanel() {
  const { t } = useI18n();
  const { data, isLoading } = useReorderReport();
  const rows = data?.items ?? [];

  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'item', label: t('field.item'), render: (r) => r.name },
    {
      key: 'onHandQty',
      label: t('manufacturing.reorder.onHand'),
      align: 'right',
      render: (r) => (
        <Text c={Number(r.onHandQty ?? 0) <= Number(r.reorderPoint ?? 0) ? 'red' : undefined}>
          {r.onHandQty}
        </Text>
      ),
    },
    {
      key: 'reorderPoint',
      label: t('manufacturing.reorder.reorderPoint'),
      align: 'right',
      render: (r) => r.reorderPoint,
    },
    { key: 'reorderQty', label: t('manufacturing.reorder.reorderQty'), align: 'right' },
  ];

  return (
    <>
      <Text size="sm" c="dimmed" mb="sm">
        {t('manufacturing.reorder.hint')}
      </Text>
      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.itemId ?? r.name ?? ''}
        isLoading={isLoading}
        emptyMessage={t('manufacturing.reorder.empty')}
      />
    </>
  );
}
