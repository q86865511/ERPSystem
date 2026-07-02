import { Badge, Card, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { BarChart, LineChart } from '@mantine/charts';
import {
  IconBuildingBank,
  IconCash,
  IconChartPie,
  IconFileInvoice,
  IconScale,
  IconUsers,
} from '@tabler/icons-react';
import { KpiTile } from '../../components/charts/KpiTile';
import { DonutCard, type DonutDatum } from '../../components/charts/DonutCard';
import { agingColors, compactNumber, moneyToNumber } from '../../components/charts/palette';
import { formatMoney } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useApAging } from '../purchasing/api';
import { useArAging } from '../sales/api';
import { ReconciliationHero } from './ReconciliationHero';
import {
  useBalanceSheet,
  useBudgetVariance,
  useCashFlow,
  useIncomeStatement,
  useKpiSummary,
  useRevenueTrend,
} from './api';

type AgingReport = {
  current?: string;
  days1to30?: string;
  days31to60?: string;
  days61to90?: string;
  days90plus?: string;
  total?: string;
};

/**
 * Finance overview dashboard (Blue Enterprise redesign, first end-to-end slice). Everything shown is
 * wired to real endpoints: AR/AP aging (donuts), income statement + balance sheet (KPIs + P&L bar), and
 * the reconciliation hero. Trend/cash-flow/budget widgets are called out as planned (need new backend).
 */
export function FinanceOverviewPanel({ asOf }: { asOf?: string }) {
  const { t } = useI18n();
  const ar = useArAging(asOf);
  const ap = useApAging(asOf);
  const is = useIncomeStatement(asOf);
  const bs = useBalanceSheet(asOf);
  const kpi = useKpiSummary(asOf);
  const revenueTrend = useRevenueTrend(6, asOf);
  const cashFlow = useCashFlow(6, asOf);
  const budget = useBudgetVariance(asOf);

  const live = (
    <Badge color="teal" variant="light" size="sm">
      {t('reporting.overview.liveData')}
    </Badge>
  );
  const agingData = (d: AgingReport | undefined): DonutDatum[] => [
    { name: t('sales.arAging.current'), amount: d?.current, color: agingColors[0] },
    { name: t('sales.arAging.days1to30'), amount: d?.days1to30, color: agingColors[1] },
    { name: t('sales.arAging.days31to60'), amount: d?.days31to60, color: agingColors[2] },
    { name: t('sales.arAging.days61to90'), amount: d?.days61to90, color: agingColors[3] },
    { name: t('sales.arAging.days90plus'), amount: d?.days90plus, color: agingColors[4] },
  ];

  const plData = [
    { metric: t('reporting.overview.plRevenue'), amount: moneyToNumber(is.data?.totalRevenue) },
    { metric: t('reporting.overview.plExpenses'), amount: moneyToNumber(is.data?.totalExpenses) },
    { metric: t('reporting.incomeStatement.netIncome'), amount: moneyToNumber(is.data?.netIncome) },
  ];

  const revTrendData = (revenueTrend.data ?? []).map((p) => ({
    month: p.month,
    revenue: moneyToNumber(p.revenue),
    grossMargin: moneyToNumber(p.grossMargin),
  }));
  const cashFlowData = (cashFlow.data ?? []).map((p) => ({
    month: p.month,
    inflow: moneyToNumber(p.inflow),
    outflow: moneyToNumber(p.outflow),
  }));
  const budgetData = (budget.data?.lines ?? []).map((l) => ({
    name: l.name ?? '',
    budget: moneyToNumber(l.budget),
    actual: moneyToNumber(l.actual),
  }));

  return (
    <Stack gap="md">
      <SimpleGrid cols={{ base: 1, xs: 2, md: 3, lg: 6 }}>
        <KpiTile label={t('reporting.overview.totalReceivables')} value={ar.data?.total} icon={<IconUsers size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.totalPayables')} value={ap.data?.total} icon={<IconFileInvoice size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.netIncome')} value={is.data?.netIncome} icon={<IconChartPie size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.totalAssets')} value={bs.data?.totalAssets} icon={<IconBuildingBank size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.totalEquity')} value={bs.data?.totalEquity} icon={<IconScale size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.cash')} value={kpi.data?.netCash?.current} icon={<IconCash size={16} />} status={live} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }}>
        <DonutCard title={t('reporting.overview.arAgingTitle')} badge={live} data={agingData(ar.data)} centerLabel={formatMoney(ar.data?.total)} />
        <DonutCard title={t('reporting.overview.apAgingTitle')} badge={live} data={agingData(ap.data)} centerLabel={formatMoney(ap.data?.total)} />
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('reporting.overview.plTitle')}</Text>
            {live}
          </Group>
          <BarChart
            h={190}
            data={plData}
            dataKey="metric"
            series={[{ name: 'amount', color: 'brand.6' }]}
            withLegend={false}
            valueFormatter={(v) => formatMoney(v)}
            yAxisProps={{ width: 52, tickFormatter: (v) => compactNumber(Number(v)) }}
            barProps={{ radius: 4 }}
          />
        </Card>
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, lg: 3 }}>
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('reporting.overview.revenueTrendTitle')}</Text>
            {live}
          </Group>
          <LineChart
            h={190}
            data={revTrendData}
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
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('reporting.overview.cashFlowTitle')}</Text>
            {live}
          </Group>
          <BarChart
            h={190}
            data={cashFlowData}
            dataKey="month"
            withLegend
            series={[
              { name: 'inflow', label: t('reporting.overview.seriesInflow'), color: 'teal.6' },
              { name: 'outflow', label: t('reporting.overview.seriesOutflow'), color: 'red.6' },
            ]}
            valueFormatter={(v) => formatMoney(v)}
            yAxisProps={{ width: 52, tickFormatter: (v) => compactNumber(Number(v)) }}
            barProps={{ radius: 3 }}
          />
        </Card>
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('reporting.overview.budgetVariance')}</Text>
            {live}
          </Group>
          <BarChart
            h={190}
            data={budgetData}
            dataKey="name"
            withLegend
            series={[
              { name: 'budget', label: t('reporting.overview.seriesBudget'), color: 'gray.5' },
              { name: 'actual', label: t('reporting.overview.seriesActual'), color: 'brand.6' },
            ]}
            valueFormatter={(v) => formatMoney(v)}
            yAxisProps={{ width: 52, tickFormatter: (v) => compactNumber(Number(v)) }}
            barProps={{ radius: 3 }}
          />
        </Card>
      </SimpleGrid>

      <ReconciliationHero asOf={asOf} />
    </Stack>
  );
}
