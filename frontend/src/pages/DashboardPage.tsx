import { Badge, Card, Group, SimpleGrid, Skeleton, Stack, Text, Title } from '@mantine/core';
import { LineChart } from '@mantine/charts';
import { IconAlertTriangle, IconBox, IconChartPie, IconClockExclamation, IconCoin, IconFileInvoice, IconUsers } from '@tabler/icons-react';
import { useAuth } from '../auth/useAuth';
import { useI18n } from '../i18n';
import { LiveBadge } from '../components/LiveBadge';
import { KpiTile, parseDeltaPct } from '../components/charts/KpiTile';
import { DonutCard, type DonutDatum } from '../components/charts/DonutCard';
import { PipelineBars } from '../components/charts/PipelineBars';
import { categoryColors, chartSeries } from '../components/charts/palette';
import { formatMoney, sumMoney } from '../components/Money';
import { ReconciliationHero } from '../features/reporting/ReconciliationHero';
import { compactNumber, moneyToNumber } from '../components/charts/palette';
import { useIncomeStatement, useKpiSummary, useRevenueTrend } from '../features/reporting/api';
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
  const revenueTrend = useRevenueTrend(6);
  const kpi = useKpiSummary();

  const kpisLoading = is.isLoading || ar.isLoading || so.isLoading || inv.isLoading;
  const netIncomeNegative = Number(is.data?.netIncome ?? 0) < 0;
  const revenueDelta = parseDeltaPct(kpi.data?.revenue?.deltaPct);

  const orders = so.data ?? [];
  const created = orders.length;
  const confirmed = orders.filter((o) => o.status !== 'DRAFT').length;
  const shipped = orders.filter((o) => SHIPPED_STATES.has(o.status ?? '')).length;
  const closed = orders.filter((o) => o.status === 'CLOSED').length;
  const funnel = [
    { name: t('dashboard.overview.stageCreated'), value: created, color: chartSeries[0] ?? 'var(--erp-chart-1)' },
    { name: t('dashboard.overview.stageConfirmed'), value: confirmed, color: chartSeries[1] ?? 'var(--erp-chart-2)' },
    { name: t('dashboard.overview.stageShipped'), value: shipped, color: chartSeries[2] ?? 'var(--erp-chart-3)' },
    { name: t('dashboard.overview.stageClosed'), value: closed, color: chartSeries[3] ?? 'var(--erp-chart-4)' },
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
        <Group gap="sm">
          <Title order={2}>{t('nav.dashboard')}</Title>
          <LiveBadge />
        </Group>
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
        {kpisLoading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <Card key={i} withBorder padding="md">
              <Skeleton height={30} width={30} radius="md" mb="md" />
              <Skeleton height={12} width="55%" mb={10} />
              <Skeleton height={22} width="80%" />
            </Card>
          ))
        ) : (
          <>
            <KpiTile
              label={t('dashboard.overview.revenue')}
              value={is.data?.totalRevenue}
              icon={<IconCoin size={16} />}
              delta={revenueDelta}
              deltaLabel={t('dashboard.kpiBasis')}
            />
            <KpiTile label={t('dashboard.netIncome')} value={is.data?.netIncome} icon={<IconChartPie size={16} />} valueColor={netIncomeNegative ? 'var(--erp-negative-text)' : undefined} />
            <KpiTile label={t('dashboard.overview.orders')} value={String(created)} money={false} icon={<IconFileInvoice size={16} />} />
            <KpiTile label={t('dashboard.overview.receivables')} value={ar.data?.total} icon={<IconUsers size={16} />} />
            <KpiTile label={t('dashboard.overview.inventoryValue')} value={invTotal} icon={<IconBox size={16} />} />
          </>
        )}
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }}>
        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('dashboard.overview.orderPipeline')}
          </Text>
          {created > 0 ? (
            <PipelineBars stages={funnel} />
          ) : (
            <Text c="dimmed" size="sm" py="xl" ta="center">
              —
            </Text>
          )}
        </Card>

        <DonutCard title={t('dashboard.overview.inventoryStatus')} data={invData} centerLabel={formatMoney(invTotal)} />

        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('dashboard.overview.alerts')}
          </Text>
          <Group justify="space-between" py={9} style={{ borderBottom: '0.5px solid var(--app-color-border)' }}>
            <Group gap={8} style={{ color: 'var(--erp-warning-text)' }}>
              <IconAlertTriangle size={16} />
              <Text size="sm">{t('dashboard.overview.lowStock')}</Text>
            </Group>
            <Text fw={500} ff="monospace">
              {lowStock}
            </Text>
          </Group>
          <Group justify="space-between" py={9}>
            <Group gap={8} style={{ color: 'var(--erp-negative-text)' }}>
              <IconClockExclamation size={16} />
              <Text size="sm">{t('dashboard.overview.overdueAr')}</Text>
            </Group>
            <Text fw={500} ff="monospace">
              {formatMoney(ar.data?.days90plus)}
            </Text>
          </Group>
        </Card>
      </SimpleGrid>

      <Card withBorder padding="md">
        <Text fw={500} mb="sm">
          {t('dashboard.overview.salesTrend')}
        </Text>
        <LineChart
          h={200}
          data={(revenueTrend.data ?? []).map((p) => ({
            month: p.month,
            revenue: moneyToNumber(p.revenue),
            grossMargin: moneyToNumber(p.grossMargin),
          }))}
          dataKey="month"
          curveType="monotone"
          withLegend
          series={[
            { name: 'revenue', label: t('reporting.overview.seriesRevenue'), color: 'brand.6' },
            { name: 'grossMargin', label: t('reporting.overview.seriesGrossMargin'), color: 'teal.6' },
          ]}
          valueFormatter={(v) => formatMoney(v)}
          yAxisProps={{ width: 52, tickFormatter: (v) => compactNumber(Number(v)) }}
        />
      </Card>

      <div data-onboarding="reconciliation-hero">
        <ReconciliationHero />
      </div>
    </Stack>
  );
}
