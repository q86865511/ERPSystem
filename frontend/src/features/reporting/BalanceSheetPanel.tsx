import { Badge, Card, Group, Loader, SimpleGrid, Stack, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { StatementSection } from './StatementSection';
import { useBalanceSheet } from './api';

export function BalanceSheetPanel({ asOf }: { asOf?: string }) {
  const { data, isLoading } = useBalanceSheet(asOf);

  if (isLoading) {
    return (
      <Group justify="center" py="xl">
        <Loader />
      </Group>
    );
  }

  return (
    <Stack>
      <Group justify="flex-end">
        <Badge color={data?.balanced ? 'teal' : 'red'} variant="light">
          {data?.balanced ? 'Assets = Liabilities + Equity' : 'Out of balance'}
        </Badge>
      </Group>
      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <StatementSection title="Assets" lines={data?.assets ?? []} total={data?.totalAssets} />
        <Stack>
          <StatementSection
            title="Liabilities"
            lines={data?.liabilities ?? []}
            total={data?.totalLiabilities}
          />
          <StatementSection title="Equity" lines={data?.equity ?? []} total={data?.totalEquity} />
        </Stack>
      </SimpleGrid>
      <Card withBorder radius="md" padding="md">
        <Group justify="space-between">
          <Text fw={700}>Total assets</Text>
          <Text fw={700} ff="monospace">
            <MoneyText value={data?.totalAssets} />
          </Text>
        </Group>
      </Card>
    </Stack>
  );
}
