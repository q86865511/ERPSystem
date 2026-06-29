import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Group, Loader, Table, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { GeneralLedgerDrawer } from './GeneralLedgerDrawer';
import { useTrialBalance } from './api';

export function TrialBalancePanel({ asOf }: { asOf?: string }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const { data, isLoading } = useTrialBalance(asOf);
  const [selected, setSelected] = useState<{ code: string; name?: string } | null>(null);
  const rows = data?.lines ?? [];

  return (
    <>
      <Group justify="space-between" mb="sm">
        <Text size="sm" c="dimmed">
          {t('reporting.trialBalance.drillHint')}
        </Text>
        <Group gap="xs">
          <Badge color={data?.balanced ? 'teal' : 'red'} variant="light">
            {data?.balanced ? t('reporting.trialBalance.balanced') : t('reporting.trialBalance.unbalanced')}
          </Badge>
          <Button
            variant="default"
            size="xs"
            onClick={() => navigate(`/print/trial-balance${asOf ? `?asOf=${asOf}` : ''}`)}
          >
            {t('print.print')}
          </Button>
        </Group>
      </Group>

      {isLoading ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('field.code')}</Table.Th>
              <Table.Th>{t('reporting.trialBalance.account')}</Table.Th>
              <Table.Th>{t('reporting.trialBalance.class')}</Table.Th>
              <Table.Th ta="right">{t('field.debit')}</Table.Th>
              <Table.Th ta="right">{t('field.credit')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((r) => (
              <Table.Tr
                key={r.code}
                style={{ cursor: 'pointer' }}
                onClick={() => r.code && setSelected({ code: r.code, name: r.name })}
              >
                <Table.Td>{r.code}</Table.Td>
                <Table.Td>{r.name}</Table.Td>
                <Table.Td>{r.accountClass}</Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={r.debit} />
                </Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={r.credit} />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
          <Table.Tfoot>
            <Table.Tr>
              <Table.Th colSpan={3}>{t('field.total')}</Table.Th>
              <Table.Th ta="right">
                <MoneyText value={data?.totalDebit} />
              </Table.Th>
              <Table.Th ta="right">
                <MoneyText value={data?.totalCredit} />
              </Table.Th>
            </Table.Tr>
          </Table.Tfoot>
        </Table>
      )}

      <GeneralLedgerDrawer
        accountCode={selected?.code ?? null}
        accountName={selected?.name}
        asOf={asOf}
        onClose={() => setSelected(null)}
      />
    </>
  );
}
