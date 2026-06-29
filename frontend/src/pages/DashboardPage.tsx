import { Badge, Card, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { MoneyText } from '../components/Money';
import { useAuth } from '../auth/useAuth';
import { useI18n } from '../i18n';
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
  const { t } = useI18n();
  const bs = useBalanceSheet();
  const is = useIncomeStatement();

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

      <ReconciliationHero />

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <Summary label={t('dashboard.totalAssets')} value={bs.data?.totalAssets} />
        <Summary label={t('dashboard.totalLiabilities')} value={bs.data?.totalLiabilities} />
        <Summary label={t('dashboard.netIncome')} value={is.data?.netIncome} />
      </SimpleGrid>
    </Stack>
  );
}
