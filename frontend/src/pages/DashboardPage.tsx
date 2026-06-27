import { Badge, Card, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { MoneyText } from '../components/Money';
import { useAuth } from '../auth/useAuth';
import { ReconciliationHero } from '../features/reporting/ReconciliationHero';
import { useBalanceSheet, useIncomeStatement } from '../features/reporting/api';

function Summary({ label, value }: { label: string; value?: string }) {
  return (
    <Card withBorder radius="md" padding="lg">
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text fw={700} fz="xl" ff="monospace">
        <MoneyText value={value} />
      </Text>
    </Card>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const bs = useBalanceSheet();
  const is = useIncomeStatement();

  return (
    <Stack gap="lg">
      <Stack gap={4}>
        <Title order={2}>Dashboard</Title>
        <Group gap="xs">
          <Text c="dimmed">Signed in as</Text>
          <Text fw={600}>{user?.username}</Text>
          {user?.roles.map((r) => (
            <Badge key={r} variant="light">
              {r}
            </Badge>
          ))}
        </Group>
      </Stack>

      <ReconciliationHero />

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <Summary label="Total assets" value={bs.data?.totalAssets} />
        <Summary label="Total liabilities" value={bs.data?.totalLiabilities} />
        <Summary label="Net income" value={is.data?.netIncome} />
      </SimpleGrid>
    </Stack>
  );
}
