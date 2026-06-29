import { Group, Loader, Table, Text } from '@mantine/core';
import { useI18n } from '../../i18n';
import { useReorderReport } from './api';

export function ReorderReportPanel() {
  const { t } = useI18n();
  const { data, isLoading } = useReorderReport();
  const rows = data?.items ?? [];

  if (isLoading) {
    return (
      <Group justify="center" py="xl">
        <Loader />
      </Group>
    );
  }

  return (
    <>
      <Text size="sm" c="dimmed" mb="sm">
        {t('manufacturing.reorder.hint')}
      </Text>
      <Table striped>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('field.item')}</Table.Th>
            <Table.Th ta="right">{t('manufacturing.reorder.onHand')}</Table.Th>
            <Table.Th ta="right">{t('manufacturing.reorder.reorderPoint')}</Table.Th>
            <Table.Th ta="right">{t('manufacturing.reorder.reorderQty')}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((r) => (
            <Table.Tr key={r.itemId}>
              <Table.Td>{r.name}</Table.Td>
              <Table.Td ta="right">{r.onHandQty}</Table.Td>
              <Table.Td ta="right">{r.reorderPoint}</Table.Td>
              <Table.Td ta="right">{r.reorderQty}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('manufacturing.reorder.empty')}
        </Text>
      )}
    </>
  );
}
