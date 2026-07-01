import { Badge, Card, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconBriefcase, IconBuilding, IconCoin, IconUsers } from '@tabler/icons-react';
import { KpiTile } from '../../components/charts/KpiTile';
import { DonutCard, type DonutDatum } from '../../components/charts/DonutCard';
import { categoryColors, moneyToNumber } from '../../components/charts/palette';
import { useI18n } from '../../i18n';
import { useDepartments, useEmployees, usePositions } from './api';

/** Read-only HR overview: headcount / departments / positions / average salary KPIs + a headcount-by-department donut. */
export function HrDashboardPanel() {
  const { t } = useI18n();
  const emp = useEmployees();
  const dep = useDepartments();
  const pos = usePositions();

  const live = (
    <Badge color="teal" variant="light" size="sm">
      {t('reporting.overview.liveData')}
    </Badge>
  );

  const money = (n: number) =>
    n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const employees = emp.data ?? [];
  const departments = dep.data ?? [];
  const positions = pos.data ?? [];
  const active = employees.filter((e) => e.status === 'ACTIVE');
  const totalPayroll = active.reduce((s, e) => s + moneyToNumber(e.monthlySalary), 0);
  const avg = active.length > 0 ? totalPayroll / active.length : 0;

  const counts = new Map<string, number>();
  for (const e of active) {
    const name = departments.find((d) => d.id === e.departmentId)?.name ?? '—';
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  const byDept: DonutDatum[] = [...counts.entries()].map(([name, count], i) => ({
    name,
    amount: String(count),
    color: categoryColors[i % categoryColors.length] ?? categoryColors[0],
  }));

  return (
    <Stack gap="md">
      <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
        <KpiTile label={t('hr.dash.headcount')} value={String(active.length)} money={false} icon={<IconUsers size={16} />} status={live} />
        <KpiTile label={t('hr.dash.departments')} value={String(departments.length)} money={false} icon={<IconBuilding size={16} />} status={live} />
        <KpiTile label={t('hr.dash.positions')} value={String(positions.length)} money={false} icon={<IconBriefcase size={16} />} status={live} />
        <KpiTile label={t('hr.dash.avgSalary')} value={money(avg)} money={false} icon={<IconCoin size={16} />} status={live} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <DonutCard title={t('hr.dash.byDepartment')} badge={live} data={byDept} centerLabel={String(active.length)} />
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('hr.dash.payroll')}</Text>
            {live}
          </Group>
          <Text fw={700} size="xl" ff="monospace">
            {money(totalPayroll)}
          </Text>
          <Text size="sm" c="dimmed" mt={4}>
            {t('hr.dash.headcount')}: {active.length}
          </Text>
        </Card>
      </SimpleGrid>
    </Stack>
  );
}
