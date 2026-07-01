import { useState } from 'react';
import { Card, Group, Loader, SimpleGrid, Stack, Table, Tabs, Text } from '@mantine/core';
import { StatTile } from '../../components';
import { ItemSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { AdjustmentsPanel } from './AdjustmentsPanel';
import { InventoryDashboardPanel } from './InventoryDashboardPanel';
import { useInventoryReconciliation, useItemOnHand } from './api';

function OverviewPanel() {
  const { t } = useI18n();
  const [itemId, setItemId] = useState<number | null>(null);
  const onHand = useItemOnHand(itemId);
  const recon = useInventoryReconciliation();
  const reconRows = recon.data ?? [];

  return (
    <Stack gap="lg">
      <Card withBorder radius="md" padding="lg">
        <Text fw={600} mb="sm">
          {t('inventory.onHandLookup')}
        </Text>
        <ItemSelect label={t('field.item')} placeholder={t('inventory.pickItem')} value={itemId} onChange={setItemId} maw={360} />
        {itemId != null && onHand.isFetching && (
          <Group mt="md">
            <Loader size="sm" />
          </Group>
        )}
        {onHand.data && (
          <SimpleGrid cols={{ base: 1, sm: 3 }} mt="md">
            <StatTile label={t('inventory.onHand')} value={onHand.data.onHandQty} money={false} />
            <StatTile label={t('inventory.avgUnitCost')} value={onHand.data.avgUnitCost} />
            <StatTile label={t('inventory.totalValue')} value={onHand.data.totalValue} />
          </SimpleGrid>
        )}
      </Card>

      <Card withBorder radius="md" padding="lg">
        <Text fw={600} mb="sm">
          {t('inventory.reconTitle')}
        </Text>
        {recon.isLoading ? (
          <Group justify="center" py="md">
            <Loader />
          </Group>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('inventory.account')}</Table.Th>
                <Table.Th ta="right">{t('inventory.subledgerValue')}</Table.Th>
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
  );
}

export function InventoryPage() {
  const { t } = useI18n();
  return (
    <>
      <PageHeader title={t('nav.inventory')} subtitle={t('inventory.subtitle')} onboardingId="module-inventory" />
      <Tabs defaultValue="dashboard" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="dashboard">{t('inventory.tabs.dashboard')}</Tabs.Tab>
          <Tabs.Tab value="overview">{t('inventory.tabs.overview')}</Tabs.Tab>
          <Tabs.Tab value="adjustments">{t('inventory.tabs.adjustments')}</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="dashboard">
          <InventoryDashboardPanel />
        </Tabs.Panel>
        <Tabs.Panel value="overview">
          <OverviewPanel />
        </Tabs.Panel>
        <Tabs.Panel value="adjustments">
          <AdjustmentsPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
