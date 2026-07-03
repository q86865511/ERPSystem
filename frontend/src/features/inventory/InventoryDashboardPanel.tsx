import { Badge, Card, Group, Progress, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconAlertTriangle, IconBox, IconReportAnalytics } from '@tabler/icons-react';
import { LiveBadge } from '../../components/LiveBadge';
import { KpiTile } from '../../components/charts/KpiTile';
import { DonutCard, type DonutDatum } from '../../components/charts/DonutCard';
import { ItemsTreemap } from '../../components/charts/ItemsTreemap';
import { categoryColors, moneyToNumber } from '../../components/charts/palette';
import { formatMoney, sumMoney } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useInventoryReconciliation, useItemsStatus } from './api';
import { useReorderReport } from '../manufacturing/api';
import { useSupplierPerformance } from '../purchasing/api';

export function InventoryDashboardPanel() {
  const { t } = useI18n();
  const recon = useInventoryReconciliation();
  const reorder = useReorderReport();
  const itemsStatus = useItemsStatus();
  const supplierPerf = useSupplierPerformance();

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

  const suppliers = supplierPerf.data ?? [];
  const totalReceipts = suppliers.reduce((s, p) => s + (p.totalReceipts ?? 0), 0);
  const totalOnTime = suppliers.reduce((s, p) => s + (p.onTime ?? 0), 0);
  const overallOnTime = totalReceipts > 0 ? `${Math.round((totalOnTime / totalReceipts) * 100)}%` : '—';

  return (
    <Stack gap="md">
      <Group justify="flex-end">
        <LiveBadge />
      </Group>

      <SimpleGrid cols={{ base: 1, xs: 3 }}>
        <KpiTile label={t('inventory.dash.inventoryValue')} value={invTotal} icon={<IconBox size={16} />} />
        <KpiTile label={t('inventory.dash.reorderCount')} value={String(items.length)} money={false} icon={<IconAlertTriangle size={16} />} />
        <KpiTile label={t('inventory.dash.supplierPerf')} value={overallOnTime} money={false} icon={<IconReportAnalytics size={16} />} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <DonutCard title={t('inventory.dash.category')} data={invData} centerLabel={formatMoney(invTotal)} />

        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('inventory.dash.reorderTitle')}
          </Text>
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
                const badgeColor = out ? 'red' : 'orange';
                const progressColor = out ? 'var(--erp-negative-text)' : 'var(--erp-warning-text)';
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
                        <Badge color={badgeColor} variant="light" size="sm">
                          {out ? t('inventory.dash.out') : t('inventory.dash.low')}
                        </Badge>
                      </Group>
                    </Group>
                    <Progress value={pct} color={progressColor} size="sm" radius="xl" />
                  </div>
                );
              })}
            </Stack>
          )}
        </Card>
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('inventory.dash.heatmap')}
          </Text>
          {treemapData.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <ItemsTreemap data={treemapData} />
          )}
        </Card>
        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('inventory.dash.supplierPerf')}
          </Text>
          {suppliers.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <Stack gap="sm">
              {suppliers.slice(0, 6).map((p) => {
                const pct = Math.round(moneyToNumber(p.onTimePct));
                const color =
                  pct >= 90 ? 'var(--erp-positive-text)' : pct >= 70 ? 'var(--erp-warning-text)' : 'var(--erp-negative-text)';
                return (
                  <div key={p.partnerId}>
                    <Group justify="space-between" gap="xs" wrap="nowrap" mb={4}>
                      <Text size="sm" truncate>
                        {p.name}
                      </Text>
                      <Text size="xs" c="dimmed" ff="monospace">
                        {pct}% ({p.onTime}/{p.totalReceipts})
                      </Text>
                    </Group>
                    <Progress value={pct} color={color} size="sm" radius="xl" />
                  </div>
                );
              })}
            </Stack>
          )}
        </Card>
      </SimpleGrid>
    </Stack>
  );
}
