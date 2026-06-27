import { Group, Loader, Table, Text } from '@mantine/core';
import { useReorderReport } from './api';

export function ReorderReportPanel() {
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
        Items at or below their reorder point.
      </Text>
      <Table striped>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Item</Table.Th>
            <Table.Th ta="right">On hand</Table.Th>
            <Table.Th ta="right">Reorder point</Table.Th>
            <Table.Th ta="right">Reorder qty</Table.Th>
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
          Nothing needs reordering.
        </Text>
      )}
    </>
  );
}
