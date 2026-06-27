import { useState } from 'react';
import { Card, Group, Loader, SimpleGrid, Stack, Table, Text } from '@mantine/core';
import { ItemSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { PageHeader } from '../../components/PageHeader';
import { useInventoryReconciliation, useItemOnHand } from './api';

function Stat({ label, value, money }: { label: string; value?: string; money?: boolean }) {
  return (
    <Card withBorder radius="md" padding="sm">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Text fw={600} ff="monospace">
        {money ? <MoneyText value={value} /> : (value ?? '—')}
      </Text>
    </Card>
  );
}

export function InventoryPage() {
  const [itemId, setItemId] = useState<number | null>(null);
  const onHand = useItemOnHand(itemId);
  const recon = useInventoryReconciliation();
  const reconRows = recon.data ?? [];

  return (
    <>
      <PageHeader title="Inventory" subtitle="On-hand stock and subledger reconciliation" />
      <Stack gap="lg">
        <Card withBorder radius="md" padding="lg">
          <Text fw={600} mb="sm">
            On-hand lookup
          </Text>
          <ItemSelect label="Item" placeholder="Pick an item" value={itemId} onChange={setItemId} maw={360} />
          {itemId != null && onHand.isFetching && (
            <Group mt="md">
              <Loader size="sm" />
            </Group>
          )}
          {onHand.data && (
            <SimpleGrid cols={{ base: 1, sm: 3 }} mt="md">
              <Stat label="On hand" value={onHand.data.onHandQty} />
              <Stat label="Avg unit cost" value={onHand.data.avgUnitCost} money />
              <Stat label="Total value" value={onHand.data.totalValue} money />
            </SimpleGrid>
          )}
        </Card>

        <Card withBorder radius="md" padding="lg">
          <Text fw={600} mb="sm">
            Inventory subledger vs GL control accounts
          </Text>
          {recon.isLoading ? (
            <Group justify="center" py="md">
              <Loader />
            </Group>
          ) : (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Account</Table.Th>
                  <Table.Th ta="right">Subledger value</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {reconRows.map((r) => (
                  <Table.Tr key={r.accountCode}>
                    <Table.Td>{r.accountCode}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={r.subledgerValue} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Card>
      </Stack>
    </>
  );
}
