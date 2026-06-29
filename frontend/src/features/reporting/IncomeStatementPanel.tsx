import { Card, Group, Loader, Stack, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { StatementSection } from './StatementSection';
import { useIncomeStatement } from './api';

export function IncomeStatementPanel({ asOf }: { asOf?: string }) {
  const { t } = useI18n();
  const { data, isLoading } = useIncomeStatement(asOf);

  if (isLoading) {
    return (
      <Group justify="center" py="xl">
        <Loader />
      </Group>
    );
  }

  return (
    <Stack>
      <StatementSection
        title={t('reporting.incomeStatement.revenue')}
        lines={data?.revenue ?? []}
        total={data?.totalRevenue}
      />
      <StatementSection
        title={t('reporting.incomeStatement.expenses')}
        lines={data?.expenses ?? []}
        total={data?.totalExpenses}
      />
      <Card withBorder radius="md" padding="md">
        <Group justify="space-between">
          <Text fw={700}>{t('reporting.incomeStatement.netIncome')}</Text>
          <Text fw={700} ff="monospace">
            <MoneyText value={data?.netIncome} />
          </Text>
        </Group>
      </Card>
    </Stack>
  );
}
