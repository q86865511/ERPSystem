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
import { useI18n } from '../../i18n';
import { useReconciliation } from './api';

function YesNo({ ok, label }: { ok: boolean | undefined; label: string }) {
  return ok ? (
    <ThemeIcon color="teal" variant="light" size="sm" radius="xl" role="img" aria-label={label}>
      <IconCheck size={14} />
    </ThemeIcon>
  ) : (
    <ThemeIcon color="red" variant="light" size="sm" radius="xl" role="img" aria-label={label}>
      <IconX size={14} />
    </ThemeIcon>
  );
}

export function ReconciliationHero({ asOf }: { asOf?: string }) {
  const { t } = useI18n();
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
            role="img"
            aria-label={healthy ? t('reporting.reconciliation.reconcile') : t('reporting.reconciliation.outOfBalance')}
          >
            {healthy ? <IconShieldCheck size={28} /> : <IconShieldX size={28} />}
          </ThemeIcon>
          <Stack gap={0}>
            <Title order={3}>
              {isLoading
                ? t('reporting.reconciliation.checking')
                : healthy
                  ? t('reporting.reconciliation.reconcile')
                  : t('reporting.reconciliation.outOfBalance')}
            </Title>
            <Text size="sm" c="dimmed">
              {data?.asOf
                ? t('reporting.reconciliation.healthCheckAsOf', { asOf: data.asOf })
                : t('reporting.reconciliation.healthCheck')}
            </Text>
          </Stack>
        </Group>
        {!isLoading && (
          <Badge
            size="lg"
            variant="light"
            color={data?.trialBalanceBalanced ? undefined : 'red'}
            styles={
              data?.trialBalanceBalanced
                ? { root: { backgroundColor: 'var(--erp-positive-bg)' }, label: { color: 'var(--erp-positive-text)' } }
                : undefined
            }
          >
            {data?.trialBalanceBalanced
              ? t('reporting.reconciliation.trialBalanceBalanced')
              : t('reporting.reconciliation.trialBalanceUnbalanced')}
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
              {t('reporting.reconciliation.subledgersTitle')}
            </Text>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('reporting.reconciliation.subledger')}</Table.Th>
                  <Table.Th>{t('reporting.trialBalance.account')}</Table.Th>
                  <Table.Th ta="right">{t('reporting.reconciliation.subledger')}</Table.Th>
                  <Table.Th ta="right">{t('reporting.reconciliation.glControl')}</Table.Th>
                  <Table.Th ta="center">{t('reporting.reconciliation.reconciled')}</Table.Th>
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
                        <YesNo
                          ok={s.reconciled}
                          label={
                            s.reconciled
                              ? t('reporting.reconciliation.reconciled')
                              : t('reporting.reconciliation.notReconciled')
                          }
                        />
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </div>

          <div>
            <Text fw={600} size="sm" mb="xs">
              {t('reporting.reconciliation.clearingTitle')}
            </Text>
            <Group gap="sm">
              {clearing.map((c) => (
                <Card key={c.accountCode} withBorder padding="sm" radius="md" miw={160}>
                  <Group justify="space-between" gap="xs">
                    <Text size="sm">{c.name ?? c.accountCode}</Text>
                    <YesNo
                      ok={c.cleared}
                      label={c.cleared ? t('reporting.reconciliation.cleared') : t('reporting.reconciliation.notCleared')}
                    />
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
