import { Badge, Card, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { BarChart } from '@mantine/charts';
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
import { useBalanceSheet, useIncomeStatement } from './api';

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

  const live = (
    <Badge color="teal" variant="light" size="sm">
      {t('reporting.overview.liveData')}
    </Badge>
  );
  const planned = (
    <Badge color="gray" variant="light" size="sm">
      {t('reporting.overview.planned')}
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

  return (
    <Stack gap="md">
      <SimpleGrid cols={{ base: 1, xs: 2, md: 3, lg: 6 }}>
        <KpiTile label={t('reporting.overview.totalReceivables')} value={ar.data?.total} icon={<IconUsers size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.totalPayables')} value={ap.data?.total} icon={<IconFileInvoice size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.netIncome')} value={is.data?.netIncome} icon={<IconChartPie size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.totalAssets')} value={bs.data?.totalAssets} icon={<IconBuildingBank size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.totalEquity')} value={bs.data?.totalEquity} icon={<IconScale size={16} />} status={live} />
        <KpiTile label={t('reporting.overview.cash')} value={undefined} icon={<IconCash size={16} />} status={planned} />
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

      <ReconciliationHero asOf={asOf} />

      <Text size="xs" c="dimmed">
        {t('reporting.overview.plTitle')} · {t('reporting.overview.cashFlowTitle')} · {t('reporting.overview.budgetVariance')} — {t('reporting.overview.plannedNote')}
      </Text>
    </Stack>
  );
}
