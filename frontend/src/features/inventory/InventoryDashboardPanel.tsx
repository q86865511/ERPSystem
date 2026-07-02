import { Badge, Card, Group, Progress, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconAlertTriangle, IconBox, IconReportAnalytics } from '@tabler/icons-react';
import { KpiTile } from '../../components/charts/KpiTile';
import { DonutCard, type DonutDatum } from '../../components/charts/DonutCard';
import { ItemsTreemap } from '../../components/charts/ItemsTreemap';
import { categoryColors, moneyToNumber } from '../../components/charts/palette';
import { formatMoney, sumMoney } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useInventoryReconciliation, useItemsStatus } from './api';
import { useReorderReport } from '../manufacturing/api';

function PlannedCard({ title, note }: { title: string; note: string }) {
  const { t } = useI18n();
  return (
    <Card withBorder padding="md">
      <Group justify="space-between" mb="sm" wrap="nowrap">
        <Text fw={500}>{title}</Text>
        <Badge color="gray" variant="light" size="sm">
          {t('reporting.overview.planned')}
        </Badge>
      </Group>
      <Text c="dimmed" size="sm" py="lg" ta="center">
        {note}
      </Text>
    </Card>
  );
}

export function InventoryDashboardPanel() {
  const { t } = useI18n();
  const recon = useInventoryReconciliation();
  const reorder = useReorderReport();
  const itemsStatus = useItemsStatus();

  const live = (
    <Badge color="teal" variant="light" size="sm">
      {t('reporting.overview.liveData')}
    </Badge>
  );

  const invLabel = (code?: string) =>
    code === '1310' ? t('dashboard.overview.raw') : code === '1320' ? t('dashboard.overview.wip') : code === '1330' ? t('dashboard.overview.finished') : (code ?? '—');
  const invData: DonutDatum[] = (recon.data ?? []).map((a, i) => ({
    name: invLabel(a.accountCode),
    amount: a.subledgerValue,
    color: categoryColors[i % categoryColors.length] ?? categoryColors[0],
  }));
  const invTotal = sumMoney((recon.data ?? []).map((a) => a.subledgerValue));

  const items = reorder.data?.items ?? [];
  const treemapData = (itemsStatus.data ?? [])
    .filter((s) => moneyToNumber(s.value) > 0)
    .map((s) => ({ name: s.sku ?? '', value: moneyToNumber(s.value), status: s.status ?? 'OK' }));

  return (
    <Stack gap="md">
      <SimpleGrid cols={{ base: 1, xs: 3 }}>
        <KpiTile label={t('inventory.dash.inventoryValue')} value={invTotal} icon={<IconBox size={16} />} status={live} />
        <KpiTile label={t('inventory.dash.reorderCount')} value={String(items.length)} money={false} icon={<IconAlertTriangle size={16} />} status={live} />
        <KpiTile label={t('inventory.dash.supplierPerf')} value={undefined} money={false} icon={<IconReportAnalytics size={16} />} status={<Badge color="gray" variant="light" size="sm">{t('reporting.overview.planned')}</Badge>} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <DonutCard title={t('inventory.dash.category')} badge={live} data={invData} centerLabel={formatMoney(invTotal)} />

        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('inventory.dash.reorderTitle')}</Text>
            {live}
          </Group>
          {items.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <Stack gap="sm">
              {items.slice(0, 6).map((it) => {
                const onHand = moneyToNumber(it.onHandQty);
                const rp = moneyToNumber(it.reorderPoint);
                const pct = rp > 0 ? Math.min(100, Math.round((onHand / rp) * 100)) : 0;
                const out = onHand <= 0;
                const color = out ? 'red' : 'orange';
                return (
                  <div key={it.itemId}>
                    <Group justify="space-between" gap="xs" wrap="nowrap" mb={4}>
                      <Text size="sm" truncate>
                        {it.name ?? it.sku}
                      </Text>
                      <Group gap={8} wrap="nowrap">
                        <Text size="xs" c="dimmed" ff="monospace">
                          {formatMoney(it.onHandQty)} / {formatMoney(it.reorderPoint)}
                        </Text>
                        <Badge color={color} variant="light" size="sm">
                          {out ? t('inventory.dash.out') : t('inventory.dash.low')}
                        </Badge>
                      </Group>
                    </Group>
                    <Progress value={pct} color={color} size="sm" radius="xl" />
                  </div>
                );
              })}
            </Stack>
          )}
        </Card>
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('inventory.dash.heatmap')}</Text>
            {live}
          </Group>
          {treemapData.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <ItemsTreemap data={treemapData} />
          )}
        </Card>
        <PlannedCard title={t('inventory.dash.supplierPerf')} note={t('reporting.overview.plannedNote')} />
      </SimpleGrid>
    </Stack>
  );
}
