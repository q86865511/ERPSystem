import { Badge, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { StatTile } from '../components';
import { useAuth } from '../auth/useAuth';
import { useI18n } from '../i18n';
import { ReconciliationHero } from '../features/reporting/ReconciliationHero';
import { useBalanceSheet, useIncomeStatement } from '../features/reporting/api';

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

      <div data-onboarding="reconciliation-hero">
        <ReconciliationHero />
      </div>

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <StatTile label={t('dashboard.totalAssets')} value={bs.data?.totalAssets} strong />
        <StatTile label={t('dashboard.totalLiabilities')} value={bs.data?.totalLiabilities} strong />
        <StatTile label={t('dashboard.netIncome')} value={is.data?.netIncome} strong />
      </SimpleGrid>
    </Stack>
  );
}
