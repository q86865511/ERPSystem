import { Badge, Card, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { FunnelChart } from '@mantine/charts';
import { IconAlertTriangle, IconBox, IconChartPie, IconClockExclamation, IconCoin, IconFileInvoice, IconUsers } from '@tabler/icons-react';
import { useAuth } from '../auth/useAuth';
import { useI18n } from '../i18n';
import { KpiTile } from '../components/charts/KpiTile';
import { DonutCard, type DonutDatum } from '../components/charts/DonutCard';
import { categoryColors, chartSeries } from '../components/charts/palette';
import { formatMoney, sumMoney } from '../components/Money';
import { ReconciliationHero } from '../features/reporting/ReconciliationHero';
import { useIncomeStatement } from '../features/reporting/api';
import { useArAging, useOrders as useSalesOrders } from '../features/sales/api';
import { useInventoryReconciliation } from '../features/inventory/api';
import { useReorderReport } from '../features/manufacturing/api';

const SHIPPED_STATES = new Set(['PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED']);

export function DashboardPage() {
  const { user } = useAuth();
  const { t } = useI18n();
  const is = useIncomeStatement();
  const ar = useArAging();
  const so = useSalesOrders();
  const inv = useInventoryReconciliation();
  const reorder = useReorderReport();

  const live = (
    <Badge color="teal" variant="light" size="sm">
      {t('reporting.overview.liveData')}
    </Badge>
  );

  const orders = so.data ?? [];
  const created = orders.length;
  const confirmed = orders.filter((o) => o.status !== 'DRAFT').length;
  const shipped = orders.filter((o) => SHIPPED_STATES.has(o.status ?? '')).length;
  const closed = orders.filter((o) => o.status === 'CLOSED').length;
  const funnel = [
    { name: t('dashboard.overview.stageCreated'), value: created, color: chartSeries[0] },
    { name: t('dashboard.overview.stageConfirmed'), value: confirmed, color: chartSeries[1] },
    { name: t('dashboard.overview.stageShipped'), value: shipped, color: chartSeries[2] },
    { name: t('dashboard.overview.stageClosed'), value: closed, color: chartSeries[3] },
  ];

  const invLabel = (code?: string) =>
    code === '1310' ? t('dashboard.overview.raw') : code === '1320' ? t('dashboard.overview.wip') : code === '1330' ? t('dashboard.overview.finished') : (code ?? '—');
  const invData: DonutDatum[] = (inv.data ?? []).map((a, i) => ({
    name: invLabel(a.accountCode),
    amount: a.subledgerValue,
    color: categoryColors[i % categoryColors.length] ?? categoryColors[0],
  }));
  const invTotal = sumMoney((inv.data ?? []).map((a) => a.subledgerValue));
  const lowStock = reorder.data?.items?.length ?? 0;

  return (
    <Stack gap="lg">
      <Stack gap={4}>
        <Title order={2}>{t('nav.dashboard')}</Title>
        <Group gap="xs">
          <Text c="dimmed">{t('dashboard.signedInAs')}</Text>
          <Text fw={600}>{user?.username}</Text>
          {user?.roles.map((r) => (
            <Badge key={r} variant="light">
              {r}
            </Badge>
          ))}
        </Group>
      </Stack>

      <SimpleGrid cols={{ base: 1, xs: 2, md: 3, lg: 5 }}>
        <KpiTile label={t('dashboard.overview.revenue')} value={is.data?.totalRevenue} icon={<IconCoin size={16} />} status={live} />
        <KpiTile label={t('dashboard.netIncome')} value={is.data?.netIncome} icon={<IconChartPie size={16} />} status={live} />
        <KpiTile label={t('dashboard.overview.orders')} value={String(created)} money={false} icon={<IconFileInvoice size={16} />} status={live} />
        <KpiTile label={t('dashboard.overview.receivables')} value={ar.data?.total} icon={<IconUsers size={16} />} status={live} />
        <KpiTile label={t('dashboard.overview.inventoryValue')} value={invTotal} icon={<IconBox size={16} />} status={live} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }}>
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('dashboard.overview.orderPipeline')}</Text>
            {live}
          </Group>
          {created > 0 ? (
            <FunnelChart data={funnel} h={200} withLabels labelsPosition="right" withTooltip />
          ) : (
            <Text c="dimmed" size="sm" py="xl" ta="center">
              —
            </Text>
          )}
        </Card>

        <DonutCard title={t('dashboard.overview.inventoryStatus')} badge={live} data={invData} centerLabel={formatMoney(invTotal)} />

        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('dashboard.overview.alerts')}</Text>
            {live}
          </Group>
          <Group justify="space-between" py={9} style={{ borderBottom: '0.5px solid var(--app-color-border)' }}>
            <Group gap={8} c="orange">
              <IconAlertTriangle size={16} />
              <Text size="sm">{t('dashboard.overview.lowStock')}</Text>
            </Group>
            <Text fw={500} ff="monospace">
              {lowStock}
            </Text>
          </Group>
          <Group justify="space-between" py={9}>
            <Group gap={8} c="red">
              <IconClockExclamation size={16} />
              <Text size="sm">{t('dashboard.overview.overdueAr')}</Text>
            </Group>
            <Text fw={500} ff="monospace">
              {formatMoney(ar.data?.days90plus)}
            </Text>
          </Group>
        </Card>
      </SimpleGrid>

      <div data-onboarding="reconciliation-hero">
        <ReconciliationHero />
      </div>

      <Text size="xs" c="dimmed">
        {t('dashboard.overview.salesTrend')} — {t('reporting.overview.plannedNote')}
      </Text>
    </Stack>
  );
}
