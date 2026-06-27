import {
  Badge,
  Card,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { IconCheck, IconShieldCheck, IconShieldX, IconX } from '@tabler/icons-react';
import { MoneyText } from '../../components/Money';
import { useReconciliation } from './api';

function YesNo({ ok }: { ok: boolean | undefined }) {
  return ok ? (
    <ThemeIcon color="teal" variant="light" size="sm" radius="xl">
      <IconCheck size={14} />
    </ThemeIcon>
  ) : (
    <ThemeIcon color="red" variant="light" size="sm" radius="xl">
      <IconX size={14} />
    </ThemeIcon>
  );
}

export function ReconciliationHero({ asOf }: { asOf?: string }) {
  const { data, isLoading, isError } = useReconciliation(asOf);

  const healthy = data?.healthy ?? false;
  const subledgers = data?.subledgerChecks ?? [];
  const clearing = data?.clearingAccounts ?? [];

  return (
    <Card withBorder radius="md" padding="lg">
      <Group justify="space-between" align="flex-start">
        <Group>
          <ThemeIcon
            size={48}
            radius="md"
            variant="light"
            color={isLoading || isError ? 'gray' : healthy ? 'teal' : 'red'}
          >
            {healthy ? <IconShieldCheck size={28} /> : <IconShieldX size={28} />}
          </ThemeIcon>
          <Stack gap={0}>
            <Title order={3}>
              {isLoading ? 'Checking the books…' : healthy ? 'Books reconcile' : 'Out of balance'}
            </Title>
            <Text size="sm" c="dimmed">
              Reconciliation health-check{data?.asOf ? ` · as of ${data.asOf}` : ''}
            </Text>
          </Stack>
        </Group>
        {!isLoading && (
          <Badge size="lg" color={data?.trialBalanceBalanced ? 'teal' : 'red'} variant="light">
            Trial balance {data?.trialBalanceBalanced ? 'balanced' : 'unbalanced'}
          </Badge>
        )}
      </Group>

      {isLoading ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : (
        <Stack gap="lg" mt="lg">
          <div>
            <Text fw={600} size="sm" mb="xs">
              Subledgers vs GL control accounts
            </Text>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Subledger</Table.Th>
                  <Table.Th>Account</Table.Th>
                  <Table.Th ta="right">Subledger</Table.Th>
                  <Table.Th ta="right">GL control</Table.Th>
                  <Table.Th ta="center">Reconciled</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {subledgers.map((s) => (
                  <Table.Tr key={s.accountCode}>
                    <Table.Td>{s.label}</Table.Td>
                    <Table.Td>{s.accountCode}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={s.subledger} />
                    </Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={s.glControl} />
                    </Table.Td>
                    <Table.Td ta="center">
                      <Group justify="center">
                        <YesNo ok={s.reconciled} />
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </div>

          <div>
            <Text fw={600} size="sm" mb="xs">
              Clearing accounts (net to zero over a complete cycle)
            </Text>
            <Group gap="sm">
              {clearing.map((c) => (
                <Card key={c.accountCode} withBorder padding="sm" radius="md" miw={160}>
                  <Group justify="space-between" gap="xs">
                    <Text size="sm">{c.name ?? c.accountCode}</Text>
                    <YesNo ok={c.cleared} />
                  </Group>
                  <Text ff="monospace" fw={600}>
                    <MoneyText value={c.balance} />
                  </Text>
                </Card>
              ))}
            </Group>
          </div>
        </Stack>
      )}
    </Card>
  );
}
