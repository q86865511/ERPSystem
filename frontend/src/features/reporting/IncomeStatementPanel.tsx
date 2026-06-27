import { Card, Group, Loader, Stack, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { StatementSection } from './StatementSection';
import { useIncomeStatement } from './api';

export function IncomeStatementPanel({ asOf }: { asOf?: string }) {
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
      <StatementSection title="Revenue" lines={data?.revenue ?? []} total={data?.totalRevenue} />
      <StatementSection title="Expenses" lines={data?.expenses ?? []} total={data?.totalExpenses} />
      <Card withBorder radius="md" padding="md">
        <Group justify="space-between">
          <Text fw={700}>Net income</Text>
          <Text fw={700} ff="monospace">
            <MoneyText value={data?.netIncome} />
          </Text>
        </Group>
      </Card>
    </Stack>
  );
}
