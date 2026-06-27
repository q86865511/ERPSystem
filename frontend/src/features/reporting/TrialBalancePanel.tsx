import { useState } from 'react';
import { Badge, Group, Loader, Table, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { GeneralLedgerDrawer } from './GeneralLedgerDrawer';
import { useTrialBalance } from './api';

export function TrialBalancePanel({ asOf }: { asOf?: string }) {
  const { data, isLoading } = useTrialBalance(asOf);
  const [selected, setSelected] = useState<{ code: string; name?: string } | null>(null);
  const rows = data?.lines ?? [];

  return (
    <>
      <Group justify="space-between" mb="sm">
        <Text size="sm" c="dimmed">
          Click an account to drill into its ledger.
        </Text>
        <Badge color={data?.balanced ? 'teal' : 'red'} variant="light">
          {data?.balanced ? 'Balanced' : 'Unbalanced'}
        </Badge>
      </Group>

      {isLoading ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Code</Table.Th>
              <Table.Th>Account</Table.Th>
              <Table.Th>Class</Table.Th>
              <Table.Th ta="right">Debit</Table.Th>
              <Table.Th ta="right">Credit</Table.Th>
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
              <Table.Th colSpan={3}>Total</Table.Th>
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
