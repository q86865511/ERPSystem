import { Badge, Card, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useAuth } from '../auth/useAuth';

const FLOW = [
  { title: 'Buy', desc: 'Purchase order → goods receipt → vendor bill → payment' },
  { title: 'Make', desc: 'BOM → work order → issue to WIP → completion at rolled cost' },
  { title: 'Sell', desc: 'Sales order → delivery → invoice → receipt' },
];

export function DashboardPage() {
  const { user } = useAuth();
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

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        {FLOW.map((f) => (
          <Card key={f.title} withBorder radius="md" padding="lg">
            <Title order={4}>{f.title}</Title>
            <Text size="sm" c="dimmed" mt="xs">
              {f.desc}
            </Text>
          </Card>
        ))}
      </SimpleGrid>

      <Card withBorder radius="md" padding="lg">
        <Text c="dimmed">
          The reconciliation health-check and financial statements land in the reporting stage.
        </Text>
      </Card>
    </Stack>
  );
}
